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
@Getter
public class GracefulShutdown {
    protected static final Logger log = LoggerFactory.getLogger(GracefulShutdown.class);

    private final String nameTag;
    private final Thread mainThread;
    private final Object shutdownMonitor = new Object();

    protected volatile ExecutorService executor;

    private long waitTime = 30L;// await 30s
    private TimeUnit waitTimeUnit;

    private Consumer<ShutDownSignalEnum> consumer;

    protected GracefulShutdown(String nameTag, Thread mainThread) {
        super();
        this.nameTag = nameTag;
        this.mainThread = mainThread;
        this.waitTimeUnit = TimeUnit.SECONDS;
    }

    public GracefulShutdown(Thread mainThread, ExecutorService executor) {
        this("Executor-Graceful", mainThread, executor);
    }

    public GracefulShutdown(String nameTag, Thread mainThread, ExecutorService executor) {
        this(nameTag, mainThread);
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
                    mainThread.interrupt();

                    //当收到停止信号时，等待主线程的执行完成
                    mainThread.join();
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

            log.info("'{}' request active count: {}", nameTag, threadPoolExecutor.getActiveCount());
            this.shutdown(threadPoolExecutor);
        } else {
            this.shutdown(this.executor);
        }
    }

    protected void shutdown(ExecutorService executorService) {
        try {
            executorService.shutdown();
            this.consumer.accept(ShutDownSignalEnum.DOWN);

            log.info("Request active thread processing, waiting ....");
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
