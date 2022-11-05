package org.mind.framework.service.queue;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.mind.framework.container.Destroyable;
import org.mind.framework.service.ExecutorFactory;
import org.mind.framework.service.Updateable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ConsumerService implements Updateable, Destroyable {

    private static final Logger log = LoggerFactory.getLogger(ConsumerService.class);

    @Setter
    private int maxPoolSize = 10;

    @Setter
    private int corePoolSize = 3;

    @Setter
    private long keepAliveTime = 60L;

    @Setter
    private int taskCapacity = 768;

    @Setter
    private int submitTaskCount = 5;

    @Setter
    @Getter
    private boolean useThreadPool = false;

    @Getter
    private volatile boolean running = false;

    @Setter
    private QueueService queueService;

    private ThreadPoolExecutor executor;

    @Override
    public void doUpdate() {
        int wholeCount = Math.min(queueService.size(), submitTaskCount);
        if (wholeCount <= 0)
            return;

        if (this.useThreadPool) {
            if (running) {
                while ((--wholeCount) >= 0)
                    executor.submit(() -> execute());
            }
            return;
        }

        this.execute();
    }

    public void initExecutorPool() {
        if (!this.useThreadPool || this.running)
            return;

        executor = ExecutorFactory.newThreadPoolExecutor(
                corePoolSize,
                Math.max(maxPoolSize, corePoolSize),
                keepAliveTime,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(taskCapacity));
        running = true;
        log.info("Init ConsumerService@{}: {}", Integer.toHexString(hashCode()), this);
    }

    @Override
    public synchronized void destroy() {
        if (this.useThreadPool && this.running) {
            this.running = false;
            log.info("Consumer-service active count: [{}]", executor.getActiveCount());
            this.executor.shutdown();
            try {
                if (!executor.awaitTermination(15L, TimeUnit.SECONDS))
                    executor.shutdownNow();
            } catch (InterruptedException e) {}
            log.info("Destroy ConsumerService@{}: {}", Integer.toHexString(hashCode()), this);
        }
    }

    private void execute() {
        try {
            DelegateMessage delegate = queueService.consumer();
            if (Objects.isNull(delegate))
                return;

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
                    .append("maxPoolSize", maxPoolSize)
                    .append(" corePoolSize", corePoolSize)
                    .append(" keepAliveTime", keepAliveTime)
                    .append(" taskCapacity", taskCapacity)
                    .append(" submitTaskCount", submitTaskCount)
                    .append(" running", running)
                    .append(" activeWorker", executor.getActiveCount())
                    .append(" completedTask", executor.getCompletedTaskCount())
                    .append(" uncompletedTask", executor.getQueue().size())
                    .toString();
        }

        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                .append("queueService", queueService.toString())
                .toString();
    }
}