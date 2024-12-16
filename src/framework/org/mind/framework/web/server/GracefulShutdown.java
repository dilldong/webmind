package org.mind.framework.web.server;

import lombok.Getter;
import org.mind.framework.service.threads.ExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author Marcus
 * @version 1.0
 * @date 2022-03-14
 */
public class GracefulShutdown {
    protected static final Logger log = LoggerFactory.getLogger(GracefulShutdown.class);

    private final Object shutdownMonitor = new Object();

    @Getter
    private final String nameTag;

    @Getter
    private final Thread currentThread;

    @Getter
    private long awaitTime;

    @Getter
    private TimeUnit awaitTimeUnit;

    protected ExecutorService executor;
    protected Consumer<ShutDownSignalStatus> consumer;

    protected GracefulShutdown(String nameTag, Thread currentThread) {
        this.nameTag = nameTag;
        this.currentThread = currentThread;
        this.awaitTime = 30L;
        this.awaitTimeUnit = TimeUnit.SECONDS;
    }

    protected GracefulShutdown(String nameTag, Thread currentThread, ExecutorService executor) {
        this(nameTag, currentThread);
        this.executor = executor;
    }

    public static GracefulShutdown newShutdown(String name, ExecutorService executor) {
        return new GracefulShutdown(name, Thread.currentThread(), executor);
    }

    public static GracefulShutdown newShutdown(String name, Thread currentThread, ExecutorService executor) {
        return new GracefulShutdown(name, currentThread, executor);
    }

    public GracefulShutdown awaitTime(long awaitTime, TimeUnit awaitTimeUnit) {
        this.awaitTime = awaitTime;
        this.awaitTimeUnit = awaitTimeUnit;
        return this;
    }

    public void registerShutdownHook() {
        this.registerShutdownHook(signal -> {});
    }

    public void registerShutdownHook(Consumer<ShutDownSignalStatus> consumer) {
        this.consumer = consumer;
        this.consumer.accept(ShutDownSignalStatus.UNSTARTED);

        Runtime.getRuntime().addShutdownHook(ExecutorFactory.newDaemonThread(nameTag, () -> {
            synchronized (shutdownMonitor) {
                log.info("Stopping the '{}' service ....", nameTag);
                this.consumer.accept(ShutDownSignalStatus.IN);

                this.onStoppingEvent();

                this.consumer.accept(ShutDownSignalStatus.OUT);

                try {
                    currentThread.interrupt();
                    currentThread.join();
                } catch (InterruptedException | IllegalStateException ignored) {
                } finally {
                    log.info("Shutdown '{}' server completed.", nameTag);
                }
            }
        }));
    }

    protected void onStoppingEvent() {
        if(Objects.isNull(this.executor))
            return;

        if (this.executor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;

            long completed = threadPoolExecutor.getCompletedTaskCount();
            log.info("'{}' active thread worker: {}, completed task: {}, remaining task: {}",
                    nameTag,
                    threadPoolExecutor.getActiveCount(),
                    completed,
                    threadPoolExecutor.getTaskCount() - completed);
            this.shutdown(threadPoolExecutor);
        } else {
            this.shutdown(this.executor);
        }
    }

    protected void shutdown(ExecutorService executorService) {
        try {
            executorService.shutdown();
            this.consumer.accept(ShutDownSignalStatus.DOWN);

            if (!executorService.awaitTermination(awaitTime, awaitTimeUnit)) {
                log.warn("'{}' didn't shutdown gracefully within '{} {}'. Proceeding with forceful shutdown",
                        nameTag,
                        awaitTime,
                        awaitTimeUnit.name());
                executorService.shutdownNow();
            }
        } catch (InterruptedException | IllegalStateException ignored) {
        }
    }
}
