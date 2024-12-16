package org.mind.framework.service.threads;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

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

    private static final String DAEMON_PREFIX = "mind-dthread-";
    private static final String USER_PREFIX = "mind-thread-";

    /**
     * The default thread-pool factory
     */
    public static final ThreadFactory DEFAULT_DAEMON_THREAD_FACTORY = ExecutorFactory.newThreadFactory(true, Thread.NORM_PRIORITY);

    public static final ThreadFactory DEFAULT_USER_THREAD_FACTORY = ExecutorFactory.newThreadFactory(false, Thread.NORM_PRIORITY);

    public static ThreadPoolExecutor newThreadPoolExecutor(int corePoolSize,
                                                           int maxPoolSize,
                                                           BlockingQueue<Runnable> workQueue) {
        return newThreadPoolExecutor(corePoolSize, maxPoolSize, workQueue, DEFAULT_USER_THREAD_FACTORY);
    }

    public static ThreadPoolExecutor newThreadPoolExecutor(int corePoolSize,
                                                           int maxPoolSize,
                                                           BlockingQueue<Runnable> workQueue,
                                                           ThreadFactory threadFactory) {
        return newThreadPoolExecutor(
                corePoolSize, maxPoolSize, KEEP_ALIVE_TIME, TimeUnit.SECONDS, workQueue, threadFactory);
    }


    public static ThreadPoolExecutor newThreadPoolExecutor(int corePoolSize,
                                                           int maxPoolSize,
                                                           long keepAliveTime,
                                                           TimeUnit unit,
                                                           BlockingQueue<Runnable> workQueue) {
        return newThreadPoolExecutor(
                corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue, DEFAULT_USER_THREAD_FACTORY);
    }


    public static ThreadPoolExecutor newThreadPoolExecutor(int corePoolSize,
                                                           int maxPoolSize,
                                                           long keepAliveTime,
                                                           TimeUnit unit,
                                                           BlockingQueue<Runnable> workQueue,
                                                           ThreadFactory threadFactory) {
        ThreadPoolExecutor executor =
                new ThreadPoolExecutor(
                        corePoolSize,
                        Math.max(maxPoolSize, corePoolSize),
                        keepAliveTime,
                        unit,
                        workQueue,
                        threadFactory);

        // pre-start all core threads
        if (corePoolSize > 0)
            executor.prestartAllCoreThreads();
        return executor;
    }


    public static ThreadPoolExecutor newThreadPoolExecutor(int corePoolSize,
                                                           int maxPoolSize,
                                                           long keepAliveTime,
                                                           TimeUnit unit,
                                                           BlockingQueue<Runnable> workQueue,
                                                           ThreadFactory threadFactory,
                                                           RejectedExecutionHandler handler) {
        ThreadPoolExecutor executor =
                new ThreadPoolExecutor(
                        corePoolSize,
                        Math.max(maxPoolSize, corePoolSize),
                        keepAliveTime, unit,
                        workQueue,
                        threadFactory,
                        handler);

        // pre-start all core threads
        if (corePoolSize > 0)
            executor.prestartAllCoreThreads();
        return executor;
    }

    public static ThreadFactory newThreadFactory(String threadNamePrefix, boolean daemon) {
        return newThreadFactory(threadNamePrefix, daemon, Thread.NORM_PRIORITY);
    }

    public static ThreadFactory newThreadFactory(boolean daemon) {
        return newThreadFactory(daemon, Thread.NORM_PRIORITY);
    }

    public static ThreadFactory newThreadFactory(boolean daemon, int priority) {
        return newThreadFactory(daemon ? DAEMON_PREFIX : USER_PREFIX, daemon, priority);
    }

    public static ThreadFactory newThreadFactory(String threadNamePrefix, boolean daemon, int priority) {
        // Executors.defaultThreadFactory();
        return new TaskThreadFactory(threadNamePrefix, daemon, priority);
    }

    public static ThreadFactory newThreadFactory(String threadGroupName, String threadNamePrefix) {
        return newThreadFactory(threadGroupName, threadNamePrefix, false);
    }

    public static ThreadFactory newThreadFactory(String threadGroupName, String threadNamePrefix, boolean daemon) {
        return newThreadFactory(threadGroupName, threadNamePrefix, daemon, Thread.NORM_PRIORITY);
    }

    public static ThreadFactory newThreadFactory(String threadGroupName, String threadNamePrefix, boolean daemon, int priority) {
        return new TaskThreadFactory(threadGroupName, threadNamePrefix, daemon, priority);
    }

    public static Thread newDaemonThread(Runnable runnable) {
        return newDaemonThread(null, runnable);
    }

    public static Thread newDaemonThread(String name, Runnable runnable) {
        return newThread(name, true, runnable);
    }

    public static Thread newThread(Runnable runnable) {
        return newThread(null, runnable);
    }

    public static Thread newThread(String name, Runnable runnable) {
        return newThread(name, false, runnable);
    }

    public static Thread newThread(String name, boolean daemon, Runnable runnable) {
        Objects.requireNonNull(runnable);
        Thread thread = daemon ?
                DEFAULT_DAEMON_THREAD_FACTORY.newThread(runnable) :
                DEFAULT_USER_THREAD_FACTORY.newThread(runnable);

        if (StringUtils.isNotEmpty(name)) {
            try {
                thread.setName(name);
            } catch (SecurityException ignored) {
            }
        }
        return thread;
    }
}
