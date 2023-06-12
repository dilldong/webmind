package org.mind.framework.service.threads;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Thread implementation that records the time at which it was created.
 */
public class TaskThread extends Thread {
    private static final Logger log = LoggerFactory.getLogger(TaskThread.class);
    private final long creationTime;

    public TaskThread(ThreadGroup group, Runnable target, String name) {
        super(group, new WrappingRunnable(target), name);
        this.creationTime = System.currentTimeMillis();
    }

    public TaskThread(ThreadGroup group, Runnable target, String name,
                      long stackSize) {
        super(group, new WrappingRunnable(target), name, stackSize);
        this.creationTime = System.currentTimeMillis();
    }

    /**
     * @return the time (in ms) at which this thread was created
     */
    public final long getCreationTime() {
        return creationTime;
    }

    /**
     * Wraps a {@link Runnable} to swallow any {@link IllegalThreadStateException}
     * instead of letting it go and potentially trigger a break in a debugger.
     */
    private static class WrappingRunnable implements Runnable {
        private Runnable wrappedRunnable;

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
                    log.debug("Thread exiting on purpose", e);
            }
        }

    }
}
