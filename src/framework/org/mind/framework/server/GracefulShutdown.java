package org.mind.framework.server;

import lombok.Getter;
import org.mind.framework.service.threads.ExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Getter
    private final String nameTag;
    @Getter
    private final Thread currentThread;

    @Getter
    private long waitTime = 30L;// await 30s
    @Getter
    private TimeUnit waitTimeUnit;

    protected volatile ExecutorService executor;
    protected Consumer<ShutDownSignalEnum> consumer;

    private final Object shutdownMonitor = new Object();

    protected GracefulShutdown(String nameTag, Thread currentThread) {
        super();
        this.nameTag = nameTag;
        this.currentThread = currentThread;
        this.waitTimeUnit = TimeUnit.SECONDS;
    }

    public GracefulShutdown(Thread currentThread, ExecutorService executor) {
        this("Executor-Graceful", currentThread, executor);
    }

    public GracefulShutdown(String nameTag, Thread currentThread, ExecutorService executor) {
        this(nameTag, currentThread);
        this.executor = executor;
    }

    public GracefulShutdown waitTime(long waitTime, TimeUnit waitTimeUnit) {
        this.waitTime = waitTime;
        this.waitTimeUnit = waitTimeUnit;
        return this;
    }

    public void registerShutdownHook() {
        this.registerShutdownHook(signal -> {});
    }

    public void registerShutdownHook(Consumer<ShutDownSignalEnum> consumer){
        this.consumer = consumer;
        this.consumer.accept(ShutDownSignalEnum.UNSTARTED);

        Runtime.getRuntime().addShutdownHook(ExecutorFactory.newThread(nameTag, true, () -> {
            synchronized (shutdownMonitor) {
                log.info("Stopping the '{}' service ....", nameTag);
                this.consumer.accept(ShutDownSignalEnum.IN);

                this.onStoppingEvent();

                this.consumer.accept(ShutDownSignalEnum.OUT);

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
            this.consumer.accept(ShutDownSignalEnum.DOWN);

            if (!executorService.awaitTermination(waitTime, waitTimeUnit)) {
                log.warn("'{}' didn't shutdown gracefully within '{} {}'. Proceeding with forceful shutdown",
                        nameTag,
                        waitTime,
                        waitTimeUnit.name());
                executorService.shutdownNow();
            }
        } catch (InterruptedException | IllegalStateException ignored) {}
    }
}
