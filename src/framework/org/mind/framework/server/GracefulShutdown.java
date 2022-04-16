package org.mind.framework.server;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
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
public class GracefulShutdown extends Thread {
    private static final Logger log = LoggerFactory.getLogger(GracefulShutdown.class);

    private Thread mainThread;
    private volatile Tomcat tomcat;
    private volatile Executor executor;
    private long waitTime = 30L;// await 30s
    private final Object shutdownMonitor = new Object();
    private boolean shutDownSignalReceived;

    public GracefulShutdown(Thread mainThread, Tomcat tomcat) {
        super();
        this.tomcat = tomcat;
        this.mainThread = mainThread;
        this.shutDownSignalReceived = false;
        this.setName("Tomcat-Graceful");
    }

    public GracefulShutdown(Thread mainThread, Executor executor) {
        super();
        this.executor = executor;
        this.mainThread = mainThread;
        this.shutDownSignalReceived = false;
        this.setName("Executor-Graceful");
    }

    public GracefulShutdown setWaitTime(long waitTime) {
        this.waitTime = waitTime;
        return this;
    }

    public boolean shutDownSignalReceived() {
        return shutDownSignalReceived;
    }

    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(this);
    }

    @Override
    public void run() {
        synchronized (shutdownMonitor) {
            log.info("Stopping the {} server ....", this.getName());
            this.shutDownSignalReceived = true;
            this.onStoppingEvent();
            try {
                mainThread.interrupt();

                //当收到停止信号时，等待mainThread的执行完成
                mainThread.join();
            } catch (InterruptedException e) {
            }
            log.info("Shutdown {} server completed.", this.getName());
        }
    }

    protected void onStoppingEvent() {
        if (Objects.nonNull(tomcat)) {
            Connector connector = this.tomcat.getConnector();
            log.info("Stopping connector is: {}", connector.toString());
            connector.pause();
            this.executor = connector.getProtocolHandler().getExecutor();
        }

        if (this.executor instanceof ThreadPoolExecutor) {
            try {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
                log.info("{} request active count: [{}]", this.getName(), threadPoolExecutor.getActiveCount());
                threadPoolExecutor.shutdown();

                log.info("Request active thread processing, waiting ....");
                if (!threadPoolExecutor.awaitTermination(waitTime, TimeUnit.SECONDS))
                    log.warn("{} thread pool did not shutdown gracefully within [{}] seconds. Proceeding with forceful shutdown", this.getName(), waitTime);

                // tomcat stopping
                if (Objects.nonNull(tomcat))
                    tomcat.stop();
            } catch (InterruptedException | LifecycleException ex) {
            }
        }
    }
}
