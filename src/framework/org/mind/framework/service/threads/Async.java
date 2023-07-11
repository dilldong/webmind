package org.mind.framework.service.threads;

import org.mind.framework.server.GracefulShutdown;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Async task facilitation
 *
 * @version 1.0
 * @auther Marcus
 * @date 2023/6/27
 */
public class Async {

    private static final ThreadPoolExecutor executor;

    static {
        executor = ExecutorFactory.newThreadPoolExecutor(
                0,
                Integer.MAX_VALUE >> 16,// 32767
                new SynchronousQueue<>(),
                ExecutorFactory.newThreadFactory("async-group", "async-pool-"));

        GracefulShutdown.newShutdown("Async-Graceful", executor)
                .waitTime(15L, TimeUnit.SECONDS)
                .registerShutdownHook();
    }

    public static ThreadPoolExecutor synchronousExecutor() {
        return executor;
    }

    public static <T> CompletableFuture<T> run(Callable<T> callable) {
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
