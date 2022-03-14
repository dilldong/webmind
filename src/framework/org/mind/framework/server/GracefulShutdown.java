package org.mind.framework.server;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private long waitTime = 30L;// await 30s
    private final Object shutdownMonitor = new Object();
    private boolean shutDownSignalReceived;

    public GracefulShutdown(Thread mainThread, Tomcat tomcat) {
        super();
        this.tomcat = tomcat;
        this.mainThread = mainThread;
        this.shutDownSignalReceived = false;
    }

    public void setWaitTime(long waitTime) {
        this.waitTime = waitTime;
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
            log.info("Stopping the tomcat server ....");
            this.shutDownSignalReceived = true;
            this.onStoppingEvent();
            try {
                mainThread.interrupt();

                //当收到停止信号时，等待mainThread的执行完成
                mainThread.join();
            } catch (InterruptedException e) {
            }
            log.info("Shutdown tomcat server completed.");
        }
    }

    protected void onStoppingEvent() {
        Connector connector = this.tomcat.getConnector();
        log.debug("Connector is: {}", connector.toString());
        connector.pause();
        Executor executor = connector.getProtocolHandler().getExecutor();

        if (executor instanceof ThreadPoolExecutor) {
            try {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
                log.debug("Protocol-Executor isTerminated: {}, {}", threadPoolExecutor.isTerminated(), threadPoolExecutor.toString());
                threadPoolExecutor.shutdown();
                if (!threadPoolExecutor.awaitTermination(waitTime, TimeUnit.SECONDS))
                    log.warn("Tomcat thread pool did not shutdown gracefully within [{}] seconds. Proceeding with forceful shutdown", waitTime);

                // tomcat stopping
                tomcat.stop();
            } catch (InterruptedException | LifecycleException ex) {
            }
        }
    }
}
