package org.mind.framework.service.threads;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.mind.framework.util.DateUtils;

/**
 * A Thread implementation that records the time at which it was created.
 */
@Slf4j
public class TaskThread extends Thread {
    @Getter
    private final long creationTime;

    public TaskThread(ThreadGroup group, Runnable target, String name) {
        this(group, target, name, 0L);
    }

    public TaskThread(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, new WrappingRunnable(target), name, stackSize);
        this.creationTime = DateUtils.CachedTime.currentMillis();
    }

    /**
     * Wraps a {@link Runnable} to swallow any {@link IllegalThreadStateException}
     * instead of letting it go and potentially trigger a break in a debugger.
     */
    private static class WrappingRunnable implements Runnable {
        private final Runnable wrappedRunnable;

        WrappingRunnable(Runnable wrappedRunnable) {
            this.wrappedRunnable = wrappedRunnable;
        }

        @Override
        public void run() {
            try {
                wrappedRunnable.run();
            } catch (IllegalThreadStateException e) {
                //expected : we just swallow the exception to avoid disturbing
                //debuggers like eclipse's
                if(log.isDebugEnabled())
                    log.debug("Thread exiting on purpose, "+ e.getMessage(), e);
            }
        }

    }
}
