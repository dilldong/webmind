package org.mind.framework.server;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.mind.framework.service.ExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * @author Marcus
 * @version 1.0
 * @date 2022-03-14
 */
public class GracefulShutdown {
    private static final Logger log = LoggerFactory.getLogger(GracefulShutdown.class);

    private final String nameTag;
    private final Thread mainThread;
    private volatile Tomcat tomcat;
    private volatile Executor executor;
    private long waitTime = 30L;// await 30s
    private TimeUnit waitTimeUnit;
    private final Object shutdownMonitor = new Object();
    private volatile boolean shutDownSignalReceived;

    private GracefulShutdown(String nameTag, Thread mainThread) {
        super();
        this.nameTag = nameTag;
        this.mainThread = mainThread;
        this.shutDownSignalReceived = false;
        this.waitTimeUnit = TimeUnit.SECONDS;
    }

    public GracefulShutdown(Thread mainThread, Tomcat tomcat) {
        this("Tomcat-Graceful", mainThread);
        this.tomcat = tomcat;
    }

    public GracefulShutdown(Thread mainThread, Executor executor) {
        this("Executor-Graceful", mainThread, executor);
    }

    public GracefulShutdown(String nameTag, Thread mainThread, Executor executor) {
        this(nameTag, mainThread);
        this.executor = executor;
    }

    public GracefulShutdown waitTime(long waitTime, TimeUnit waitTimeUnit) {
        this.waitTime = waitTime;
        this.waitTimeUnit = waitTimeUnit;
        return this;
    }

    public boolean shutDownSignalReceived() {
        return shutDownSignalReceived;
    }

    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(ExecutorFactory.newThread(nameTag, true, () -> {
            synchronized (shutdownMonitor) {
                log.info("Stopping the {} service ....", nameTag);
                this.shutDownSignalReceived = true;
                this.onStoppingEvent();
                try {
                    mainThread.interrupt();

                    //当收到停止信号时，等待主线程的执行完成
                    mainThread.join();
                } catch (InterruptedException | IllegalStateException e) {}
                log.info("Shutdown {} server completed.", nameTag);
            }
        }));
    }

    protected void onStoppingEvent() {
        if (Objects.nonNull(tomcat)) {
            Connector connector = this.tomcat.getConnector();
            log.info("Stopping connector is: {}", connector.toString());
            connector.pause();
            this.executor = connector.getProtocolHandler().getExecutor();
        }

        // This Executor object
        if (this.executor instanceof ThreadPoolExecutor) {
            try {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
                log.info("{} request active count: [{}]", nameTag, threadPoolExecutor.getActiveCount());
                threadPoolExecutor.shutdown();

                log.info("Request active thread processing, waiting ....");
                if (!threadPoolExecutor.awaitTermination(waitTime, waitTimeUnit)) {
                    log.warn("{} didn't shutdown gracefully within [{}] {}. Proceeding with forceful shutdown",
                            nameTag,
                            waitTime,
                            waitTimeUnit.name());
                    threadPoolExecutor.shutdownNow();
                }
            } catch (InterruptedException | IllegalStateException e) {
            }
        }else if(this.executor instanceof java.util.concurrent.ThreadPoolExecutor){
            try {
                java.util.concurrent.ThreadPoolExecutor threadPoolExecutor =
                        (java.util.concurrent.ThreadPoolExecutor) executor;
                log.info("{} request active count: [{}]", nameTag, threadPoolExecutor.getActiveCount());
                threadPoolExecutor.shutdown();

                log.info("Request active thread processing, waiting ....");
                if (!threadPoolExecutor.awaitTermination(waitTime, waitTimeUnit)) {
                    log.warn("{} didn't shutdown gracefully within [{}] {}. Proceeding with forceful shutdown",
                            nameTag,
                            waitTime,
                            waitTimeUnit.name());
                    threadPoolExecutor.shutdownNow();
                }
            } catch (InterruptedException | IllegalStateException e) {
            }
        }

        // tomcat stopping(see TomcatServer: stop(), destroy())
        if (Objects.nonNull(tomcat)) {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (LifecycleException e) {
            }
        }
    }
}
