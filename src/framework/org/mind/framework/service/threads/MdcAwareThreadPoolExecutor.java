package org.mind.framework.service.threads;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    public <T> Future<T> submit(@NotNull Callable<T> task) {
        return super.submit(ThreadContextPropagator.wrap(task));
    }

    @Override
    public Future<?> submit(@NotNull Runnable task) {
        return super.submit(ThreadContextPropagator.wrap(task));
    }
}
