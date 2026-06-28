package org.mind.framework.service.threads;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author: Marcus
 * @date: 2026/5/27
 * @version: 1.0
 */
public class MdcAwareExecutorService implements ExecutorService {
    private final ExecutorService delegate;

    public MdcAwareExecutorService(ExecutorService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(@NotNull Runnable command) {
        delegate.execute(ThreadContextPropagator.wrap(command));
    }

    @Override
    public <T> Future<T> submit(@NotNull Callable<T> task) {
        return delegate.submit(ThreadContextPropagator.wrapCallable(task));
    }

    @Override
    public <T> Future<T> submit(@NotNull Runnable task, T result) {
        return delegate.submit(ThreadContextPropagator.wrap(task), result);
    }

    @Override
    public Future<?> submit(@NotNull Runnable task) {
        return delegate.submit(ThreadContextPropagator.wrap(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(wrapAll(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks,
                                         long timeout,
                                         @NotNull TimeUnit unit) throws InterruptedException {
        return delegate.invokeAll(wrapAll(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(wrapAll(tasks));
    }

    @Override
    public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks,
                           long timeout,
                           @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(wrapAll(tasks), timeout, unit);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    private <T> List<Callable<T>> wrapAll(Collection<? extends Callable<T>> tasks) {
        return tasks.stream()
                .map(ThreadContextPropagator::wrapCallable)
                .toList();
    }
}
