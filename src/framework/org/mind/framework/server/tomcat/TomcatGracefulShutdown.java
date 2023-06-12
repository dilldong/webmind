package org.mind.framework.server.tomcat;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.mind.framework.server.GracefulShutdown;
import org.mind.framework.server.ShutDownSignalEnum;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2023/6/12
 */
public class TomcatGracefulShutdown extends GracefulShutdown {
    private volatile Tomcat tomcat;

    public TomcatGracefulShutdown(Thread mainThread, Tomcat tomcat) {
        super("Tomcat-Graceful", mainThread);
        this.tomcat = tomcat;
    }

    @Override
    protected void onStoppingEvent() {
        Executor executor = null;
        if (Objects.nonNull(tomcat)) {
            Connector connector = this.tomcat.getConnector();
            log.info("Stopping connector is: {}", connector.toString());
            connector.pause();
            this.getConsumer().accept(ShutDownSignalEnum.PAUSE);
            executor = connector.getProtocolHandler().getExecutor();
        }

        if(Objects.nonNull(executor)) {
            ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
            log.info("'{}' request active count: {}", this.getNameTag(), threadPoolExecutor.getActiveCount());
            super.shutdown(threadPoolExecutor);
        }

        // tomcat stopping(see TomcatServer: stop(), destroy())
        if(Objects.nonNull(tomcat)) {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (LifecycleException ignored) {}
        }
    }
}
