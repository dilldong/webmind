package org.mind.framework.service.queue;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.mind.framework.container.Destroyable;
import org.mind.framework.server.GracefulShutdown;
import org.mind.framework.server.ShutDownSignalEnum;
import org.mind.framework.service.Updatable;
import org.mind.framework.service.threads.Async;
import org.mind.framework.service.threads.ExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ConsumerService implements Updatable, Destroyable {

    private static final Logger log = LoggerFactory.getLogger(ConsumerService.class);

    private final Object executObject = new Object();

    @Setter
    private int poolSize = Runtime.getRuntime().availableProcessors() << 3;

    /**
     * if taskCapacity = 0, then SynchronousQueue
     */
    @Setter
    private int taskCapacity;

    /**
     * The submitted worker threads
     */
    @Setter
    private int submitTask = 5;

    @Setter
    @Getter
    private boolean useThreadPool = false;

    @Getter
    private volatile boolean running = false;

    @Setter
    private transient QueueService queueService;

    private transient ThreadPoolExecutor executor;

    @Override
    public void doUpdate() {
        synchronized (executObject) {
            if (this.useThreadPool) {
                int wholeCount = Math.min(queueService.size(), submitTask);
                if (wholeCount < 1)
                    return;

                if (running) {
                    while ((--wholeCount) >= 0)
                        this.submitTask(this::execute);
                }
                return;
            }

            this.execute();
        }
    }

    public void initExecutorPool() {
        if (!this.useThreadPool || this.running)
            return;

        if (taskCapacity > 0) {
            executor = ExecutorFactory.newThreadPoolExecutor(
                    0,
                    poolSize,
                    new LinkedBlockingQueue<>(taskCapacity));

            GracefulShutdown.newShutdown("Consumer-Graceful", executor)
                    .waitTime(15L, TimeUnit.SECONDS)
                    .registerShutdownHook(signal -> {
                        if (signal == ShutDownSignalEnum.IN)
                            this.running = false;
                    });
        } else
            executor = Async.synchronousExecutor();

        running = true;
        log.info("Initialize the thread pool consumer service: {}", this.simplePoolInfo());
    }

    @Override
    public void destroy() {

    }

    private void submitTask(Runnable task) {
        if (Objects.isNull(task))
            return;

        executor.submit(task);
    }

    private void execute() {
        try {
            DelegateMessage delegate = queueService.consumer();
            if (Objects.isNull(delegate))
                return;

            if (log.isDebugEnabled())
                log.debug("Consumer queue message....");

            try {
                delegate.process();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        if (this.useThreadPool) {
            return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                    .append("poolSize", poolSize)
                    .append(" taskCapacity", taskCapacity)
                    .append(" submitTaskCount", submitTask)
                    .append(" running", running)
                    .append(" activeWorker", executor.getActiveCount())
                    .append(" completedTask", executor.getCompletedTaskCount())
                    .append(" remainingTask", executor.getTaskCount() - executor.getCompletedTaskCount())
                    .toString();
        }

        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                .append("queueService", queueService.toString())
                .toString();
    }

    private String simplePoolInfo() {
        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                .append("poolSize", poolSize)
                .append(" taskCapacity", taskCapacity)
                .append(" submitTaskCount", submitTask)
                .toString();
    }
}