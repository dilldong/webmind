package org.mind.framework.web.async;

import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.service.threads.MdcAwareThreadPoolExecutor;
import org.mind.framework.service.threads.ThrottleRejectionHandler;
import org.mind.framework.web.server.GracefulShutdown;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 *
 * @author: Marcus
 * @date: 2026/6/8
 * @version: 1.0
 */
public class AsyncServletExecutorService {
    private final ThreadPoolExecutor executor;

    public AsyncServletExecutorService() {
        this(new MdcAwareThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors() << 1,
                Runtime.getRuntime().availableProcessors() << 2,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(512),
                ExecutorFactory.newThreadFactory("async-http-group", "async-http-"),
                new ThrottleRejectionHandler()// 背压：拒绝时在调用方线程执行（即 IO 线程）
        ));
    }

    public AsyncServletExecutorService(ThreadPoolExecutor executor) {
        this.executor = executor;
        this.executor.allowCoreThreadTimeOut(false);
        this.executor.prestartAllCoreThreads();

        GracefulShutdown.newShutdown("Async-Servlet", this.executor)
                .awaitTime(15L, TimeUnit.SECONDS)
                .registerShutdownHook();
    }

    /**
     * 带 AsyncContext 的提交入口
     * 拒绝时可精确写 429
     */
    public <T> CompletableFuture<T> submit(AsyncRequestContext asyncContext, Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();

        AsyncTask asyncTask = new AsyncTask(asyncContext, () -> {
            Map<String, String> prev = MDC.getCopyOfContextMap();
            try {
                if (Objects.nonNull(asyncContext.getMdcSnapshot()))
                    MDC.setContextMap(asyncContext.getMdcSnapshot());

                future.complete(task.get());
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
            } finally {
                MDC.clear();
                if (Objects.nonNull(prev))
                    MDC.setContextMap(prev);
            }
        });

        try {
            executor.execute(asyncTask);
        } catch (RejectedExecutionException ex) {
            // 如果拒绝策略抛出异常，在这里兜底
            future.completeExceptionally(ex);
        }

        return future;
    }
}
