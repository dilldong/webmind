package org.mind.framework.service.threads;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple task thread factory to use to create threads for an executor
 * implementation.
 */
@NoArgsConstructor
public class TaskThreadFactory implements ThreadFactory {
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    @Getter
    @Setter
    private ThreadGroup group;

    @Getter
    @Setter
    private String threadNamePrefix;

    @Getter
    @Setter
    private boolean daemon = false;

    @Getter
    @Setter
    private int threadPriority = Thread.NORM_PRIORITY;

    public TaskThreadFactory(String threadNamePrefix){
        this(threadNamePrefix, false, Thread.NORM_PRIORITY);
    }

    public TaskThreadFactory(String threadGroupName, String threadNamePrefix){
        this(threadGroupName, threadNamePrefix, false, Thread.NORM_PRIORITY);
    }

    public TaskThreadFactory(String threadNamePrefix, boolean daemon, int priority) {
        this.group = Thread.currentThread().getThreadGroup();
        this.threadNamePrefix = threadNamePrefix;
        this.daemon = daemon;
        this.threadPriority = priority;
    }

    public TaskThreadFactory(String threadGroupName, String threadNamePrefix, boolean daemon, int priority) {
        // child thread group
        this.group = new ThreadGroup(threadGroupName);
        this.threadNamePrefix = threadNamePrefix;
        this.daemon = daemon;
        this.threadPriority = priority;
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
        TaskThread t = new TaskThread(group, r, threadNamePrefix + threadNumber.getAndIncrement());
        t.setDaemon(daemon);
        t.setPriority(threadPriority);
        t.setContextClassLoader(getClass().getClassLoader());
        return t;
    }
}
