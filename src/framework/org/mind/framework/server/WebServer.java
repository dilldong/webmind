package org.mind.framework.server;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.mind.framework.server.tomcat.TomcatGracefulShutdown;

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
                serverConfig.getTomcatBaseDir());

//        AnnotationConfigApplicationContext
//        AnnotationConfigWebApplicationContext ac = new AnnotationConfigWebApplicationContext();
//        ac.register(mainClass);
//        ac.refresh();

        // Monitor tomcat to stop, Use kill -15 to stop the service gracefully
        GracefulShutdown shutdown = new TomcatGracefulShutdown(Thread.currentThread(), tomcat);
        shutdown.registerShutdownHook();
    }
}
