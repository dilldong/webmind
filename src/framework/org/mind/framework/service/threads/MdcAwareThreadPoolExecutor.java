package org.mind.framework.service.threads;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * @author Marcus
 * @version 1.0
 * @date 2026/5/21
 */
public class MdcAwareThreadPoolExecutor extends ThreadPoolExecutor {

    public MdcAwareThreadPoolExecutor(int corePoolSize,
                                      int maximumPoolSize,
                                      long keepAliveTime,
                                      TimeUnit unit,
                                      BlockingQueue<Runnable> workQueue,
                                      ThreadFactory threadFactory,
                                      RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }


    @Override
    public void execute(@NotNull Runnable command) {
        super.execute(ThreadContextPropagator.wrap(command));
    }

    @Override
    public <T> Future<T> submit(@NotNull Callable<T> callable) {
        return super.submit(ThreadContextPropagator.wrapCallable(callable));
    }

    @Override
    public Future<?> submit(@NotNull Runnable task) {
        return super.submit(ThreadContextPropagator.wrap(task));
    }

    @Override
    public <T> Future<T> submit(@NotNull Runnable task, T result) {
        return super.submit(ThreadContextPropagator.wrap(task), result);
    }

    @Override
    public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return super.invokeAll(wrapAll(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks,
                                         long timeout,
                                         @NotNull TimeUnit unit) throws InterruptedException {
        return super.invokeAll(wrapAll(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return super.invokeAny(wrapAll(tasks));
    }

    @Override
    public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks,
                           long timeout,
                           @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return super.invokeAny(wrapAll(tasks), timeout, unit);
    }

    private <T> List<Callable<T>> wrapAll(Collection<? extends Callable<T>> tasks) {
        return tasks.stream()
                .map(ThreadContextPropagator::wrapCallable)
                .collect(Collectors.toList());
    }
}
