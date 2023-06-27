package org.mind.framework.service.threads;

import org.mind.framework.server.GracefulShutdown;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Async task facilitation
 *
 * @version 1.0
 * @auther Marcus
 * @date 2023/6/27
 */
public class Async {

    private static final ExecutorService executor;

    static {
        executor = Executors.newCachedThreadPool(ExecutorFactory.DEFAULT_USER_THREAD_FACTORY);
        new GracefulShutdown("Async-Graceful", Thread.currentThread(), executor)
                .waitTime(12L, TimeUnit.SECONDS)
                .registerShutdownHook();
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

    public static int getCpuCount() {
        return Runtime.getRuntime().availableProcessors();
    }

}
