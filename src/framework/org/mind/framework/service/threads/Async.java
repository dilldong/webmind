package org.mind.framework.service.threads;

import org.mind.framework.web.server.GracefulShutdown;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Async task facilitation
 *
 * @version 1.0
 * @author Marcus
 * @date 2023/6/27
 */
public class Async {

    private static final ThreadPoolExecutor SYNCHRONOUS_EXECUTOR;

    static {
        SYNCHRONOUS_EXECUTOR = ExecutorFactory.newThreadPoolExecutor(
                0,
                1024,
                30L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                ExecutorFactory.newThreadFactory("async-group", "async-pool-"),
                new ThreadPoolExecutor.CallerRunsPolicy());

        GracefulShutdown.newShutdown("Async-Graceful", SYNCHRONOUS_EXECUTOR)
                .awaitTime(15L, TimeUnit.SECONDS)
                .registerShutdownHook();
    }

    public static ThreadPoolExecutor synchronousExecutor() {
        return SYNCHRONOUS_EXECUTOR;
    }

    public static <T> CompletableFuture<T> run(Callable<T> callable) {
        return run(callable, SYNCHRONOUS_EXECUTOR);
    }

    public static <T> CompletableFuture<T> run(Callable<T> callable, Executor executor) {
        CompletableFuture<T> result = new CompletableFuture<>();
        CompletableFuture.runAsync(
                () -> {
                    // we need to explicitly catch any exceptions,
                    // otherwise they will be silently discarded
                    try {
                        result.complete(callable.call());
                    } catch (Throwable e) {
                        result.completeExceptionally(e);
                    }
                }, executor);
        return result;
    }
}
