package org.mind.framework.service.queue;

import lombok.Getter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.mind.framework.service.threads.Async;
import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.web.server.GracefulShutdown;
import org.mind.framework.web.server.ShutDownSignalStatus;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MultiTaskConsumerService extends ConsumerService {

    @Getter
    private final int maxPoolSize;

    @Getter
    private volatile boolean running = false;

    private ThreadPoolExecutor executor;

    public MultiTaskConsumerService(QueueService queueService) {
        this(3, queueService);
    }

    public MultiTaskConsumerService(int maxPoolSize, QueueService queueService) {
        super(queueService);
        this.maxPoolSize = maxPoolSize;
        this.initExecutorPool();
    }

    @Override
    public void doUpdate() {
        if (queueService.isEmpty())
            return;

        int wholeCount = Math.min(queueService.size(), maxPoolSize);
        if (wholeCount < 1)
            return;

        if (running) {
            while ((--wholeCount) > -1) {
                if(executor.getQueue().remainingCapacity() == 0)
                    continue;
                this.executor.execute(this::consumption);
            }
        }
    }

    private void initExecutorPool() {
        if (this.running)
            return;

        if (maxPoolSize > 0) {
            executor = ExecutorFactory.newThreadPoolExecutor(
                    maxPoolSize, maxPoolSize,
                    0, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(maxPoolSize));

            GracefulShutdown.newShutdown("Consumer-Graceful", executor)
                    .waitTime(15L, TimeUnit.SECONDS)
                    .registerShutdownHook(signal -> {
                        if (signal == ShutDownSignalStatus.IN)
                            this.running = false;
                    });
        } else
            executor = Async.synchronousExecutor();

        running = true;
        log.info("Initialize the thread pool consumer service: {}", this.simplePoolInfo());
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                .append("corePoolSize", executor.getCorePoolSize())
                .append(" maxPoolSize", executor.getMaximumPoolSize())
                .append(" workerCapacity", maxPoolSize)
                .append(" submitTaskCount", maxPoolSize)
                .append(" running", running)
                .append(" activeWorker", executor.getActiveCount())
                .append(" completedTask", executor.getCompletedTaskCount())
                .append(" remainingTask", executor.getQueue().size())
                .toString();
    }

    private String simplePoolInfo() {
        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                .append("corePoolSize", executor.getCorePoolSize())
                .append(" maxPoolSize", executor.getMaximumPoolSize())
                .append(" workerCapacity", maxPoolSize)
                .append(" submitTaskCount", maxPoolSize)
                .toString();
    }
}