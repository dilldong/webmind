package org.mind.framework.service.queue;

import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.tomcat.util.threads.TaskThreadFactory;
import org.mind.framework.service.Updateable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ConsumerService implements Updateable {

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
    private boolean useThreadPool = false;

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

        BlockingQueue<Runnable> taskqueue = new LinkedBlockingQueue<>(taskCapacity);
        ThreadFactory tf = new TaskThreadFactory("exec-", true, Thread.NORM_PRIORITY);
        executor = new ThreadPoolExecutor(
                corePoolSize,
                Math.max(maxPoolSize, corePoolSize),
                keepAliveTime,
                TimeUnit.SECONDS,
                taskqueue,
                tf);

        executor.prestartAllCoreThreads();// 预启动所有核心线程
        running = true;
        log.info("Init QueueService exec-pool-executor: {}", this);
    }

    @Override
    public void destroy() {
        if (this.useThreadPool && this.running) {
            this.running = false;
            this.executor.shutdown();
            log.info("Destroy QueueService exec-pool-executor: {}", this);
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
            return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                    .append("maxPoolSize", maxPoolSize)
                    .append("corePoolSize", corePoolSize)
                    .append("keepAliveTime", keepAliveTime)
                    .append("taskCapacity", taskCapacity)
                    .append("submitTaskCount", submitTaskCount)
                    .append("running", running)
                    .toString();
        }

        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("queueService", queueService.toString())
                .toString();
    }
}