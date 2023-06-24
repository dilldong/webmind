package org.mind.framework.service.threads;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2022/11/5
 */
@Slf4j
public class ExecutorFactory {
    private static final long KEEP_ALIVE_TIME = 60L;

    private static final String NAME_PREFIX = "mind-pool-";

    /**
     * The default rejected execution handler
     */
    private static final RejectedExecutionHandler DEFAULT_REJECT_HANDLER = new ThreadPoolExecutor.AbortPolicy();

    /**
     * The default thread-pool factory
     */
    public static final ThreadFactory DEFAULT_DAEMON_THREAD_FACTORY = ExecutorFactory.newThreadFactory(true, Thread.NORM_PRIORITY);

    public static final ThreadFactory DEFAULT_USER_THREAD_FACTORY = ExecutorFactory.newThreadFactory(false, Thread.NORM_PRIORITY);

    public static ThreadPoolExecutor newThreadPoolExecutor(int corePoolSize,
                                                           int maxPoolSize,
                                                           BlockingQueue<Runnable> taskRunnables) {
        return newThreadPoolExecutor(corePoolSize, maxPoolSize, KEEP_ALIVE_TIME, TimeUnit.SECONDS, taskRunnables);
    }

    public static ThreadPoolExecutor newThreadPoolExecutor(int corePoolSize,
                                                           int maxPoolSize,
                                                           long keepAliveTime,
                                                           TimeUnit unit,
                                                           BlockingQueue<Runnable> taskRunnables) {
        return newThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                unit,
                taskRunnables,
                DEFAULT_DAEMON_THREAD_FACTORY,
                DEFAULT_REJECT_HANDLER);
    }

    public static ThreadPoolExecutor newThreadPoolExecutor(int corePoolSize,
                                                           int maxPoolSize,
                                                           long keepAliveTime,
                                                           TimeUnit unit,
                                                           BlockingQueue<Runnable> taskRunnables,
                                                           ThreadFactory threadFactory,
                                                           RejectedExecutionHandler handler) {
        ThreadPoolExecutor executor =
                new ThreadPoolExecutor(
                        corePoolSize,
                        Math.max(maxPoolSize, corePoolSize),
                        keepAliveTime, unit,
                        taskRunnables,
                        threadFactory,
                        handler);

        // Prestart all core threads
        executor.prestartAllCoreThreads();
        return executor;
    }

    public static ThreadFactory newThreadFactory(boolean daemon, int priority) {
        return new TaskThreadFactory(NAME_PREFIX, daemon, priority);
    }

    public static ThreadFactory newThreadFactory(String threadNamePrefix, boolean daemon, int priority) {
        // Executors.defaultThreadFactory();
        return new TaskThreadFactory(threadNamePrefix, daemon, priority);
    }

    public static Thread newThread(Runnable runnable) {
        return newThread(ThreadFactory.class.getSimpleName(), runnable);
    }

    public static Thread newThread(String name, Runnable runnable) {
        return newThread(name, false, runnable);
    }

    public static Thread newThread(String name, boolean daemon, Runnable runnable) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(runnable);
        Thread thread = daemon ?
                DEFAULT_DAEMON_THREAD_FACTORY.newThread(runnable) :
                DEFAULT_USER_THREAD_FACTORY.newThread(runnable);
        try {
            thread.setName(name);
        } catch (SecurityException ignored) {}
        return thread;
    }

}
