package org.mind.framework.service.threads;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple task thread factory to use to create threads for an executor
 * implementation.
 */
@NoArgsConstructor
public class TaskThreadFactory implements ThreadFactory {
    @Getter
    @Setter
    private ThreadGroup group;

    private final AtomicInteger threadNumber = new AtomicInteger(1);

    @Getter
    @Setter
    private String namePrefix;

    @Getter
    @Setter
    private boolean daemon;

    @Getter
    @Setter
    private int threadPriority;

    public TaskThreadFactory(String namePrefix, boolean daemon, int priority) {
        this.group = Thread.currentThread().getThreadGroup();
        this.namePrefix = namePrefix;
        this.daemon = daemon;
        this.threadPriority = priority;
    }

    public TaskThreadFactory(String threadGroupName, String namePrefix, boolean daemon, int priority) {
        // child thread group
        this.group = new ThreadGroup(threadGroupName);
        this.namePrefix = namePrefix;
        this.daemon = daemon;
        this.threadPriority = priority;
    }

    @Override
    public Thread newThread(Runnable r) {
        TaskThread t = new TaskThread(group, r, namePrefix + threadNumber.getAndIncrement());
        t.setDaemon(daemon);
        t.setPriority(threadPriority);
        t.setContextClassLoader(getClass().getClassLoader());
        return t;
    }
}
