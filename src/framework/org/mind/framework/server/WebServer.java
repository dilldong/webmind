package org.mind.framework.server;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;

/**
 * @author Marcus
 * @version 1.1
 */
public class WebServer extends ServerContext {

    public WebServer() {
        super();
    }

    @Override
    protected void registerServer(Tomcat tomcat, WebServerConfig serverConfig) throws LifecycleException {
        // register webapp
        tomcat.addWebapp(
                tomcat.getHost(),
                serverConfig.getContextPath(),
                createTempDir(serverConfig.getServerName()).getAbsolutePath());

        // Monitor tomcat to stop, Use kill -15 to stop the service gracefully
        GracefulShutdown shutdown = new GracefulShutdown(Thread.currentThread(), tomcat);
        shutdown.registerShutdownHook();
    }
}
