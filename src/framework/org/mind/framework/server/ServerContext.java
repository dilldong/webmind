package org.mind.framework.server;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Server;
import org.apache.catalina.core.JreMemoryLeakPreventionListener;
import org.apache.catalina.core.ThreadLocalLeakPreventionListener;
import org.apache.catalina.mbeans.GlobalResourcesLifecycleListener;
import org.apache.catalina.startup.Tomcat;
import org.apache.commons.lang.StringUtils;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.exception.WebServerException;
import org.mind.framework.server.tomcat.TomcatServer;
import org.mind.framework.util.DateFormatUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Marcus
 * @version 1.0
 */
@Slf4j
public abstract class ServerContext {
    public static final String SERVLET_NAME = "mindDispatcher";
    public static final String SERVLET_CLASS = "org.mind.framework.dispatcher.DispatcherServlet";

    private transient Set<String> springFileSet;
    private transient Set<String> resourceSet;

    private final transient WebServerConfig serverConfig;

    private final Object monitor = new Object();

    /**
     * Register tomcat-server you custom
     *
     * @param tomcat
     * @throws LifecycleException
     */
    protected abstract void registerServer(Tomcat tomcat, WebServerConfig serverConfig) throws LifecycleException;

    public ServerContext() {
        this.serverConfig = WebServerConfig.init();
    }

    public void startup() throws WebServerException {
        synchronized (this.monitor) {
            try {
                final long begin = DateFormatUtils.getTimeMillis();
                Tomcat tomcat = creationServer();
                this.registerServer(tomcat, serverConfig);

                // use JNDI sevice
                tomcat.enableNaming();

                // Prevent memory leaks due to use of particular java/javax APIs
                Server server = tomcat.getServer();
                server.addLifecycleListener(new JreMemoryLeakPreventionListener());
                server.addLifecycleListener(new GlobalResourcesLifecycleListener());
                server.addLifecycleListener(new ThreadLocalLeakPreventionListener());

                // Unlike Jetty, all Tomcat threads are daemon threads. We create a
                // blocking non-daemon to stop immediate shutdown
                tomcat.start();

                log.info("Starting Protocol: [{}], [{}]",
                        serverConfig.getPort(),
                        StringUtils.isEmpty(serverConfig.getContextPath()) ? "/" : serverConfig.getContextPath());

                log.info("{} startup time: {}ms", serverConfig.getServerName(), (DateFormatUtils.getTimeMillis() - begin));
            } catch (Exception e) {
                throw new WebServerException("Unable to start embedded Tomcat", e);
            }
        }
    }

    public ServerContext addSpringFile(String... filePath) {
        if (filePath == null || filePath.length == 0)
            return this;

        if (this.springFileSet == null)
            this.springFileSet = new HashSet<>();

        this.springFileSet.addAll(Arrays.asList(filePath));
        return this;
    }

    public ServerContext addResource(String... resPath) {
        if (resPath == null || resPath.length == 0)
            return this;

        if (this.resourceSet == null)
            this.resourceSet = new HashSet<>();

        this.resourceSet.addAll(Arrays.asList(resPath));
        return this;
    }

    protected Tomcat creationServer() {
        serverConfig.setResourceSet(resourceSet);
        serverConfig.setSpringFileSet(springFileSet);

        if (StringUtils.isEmpty(serverConfig.getTomcatBaseDir()))
            serverConfig.setTomcatBaseDir(createTempDir(serverConfig.getServerName()).getAbsolutePath());

        TomcatServer tomcat = new TomcatServer(serverConfig);

        // logging server
        tomcat.logServer();

        return tomcat;
    }

    /**
     * Return the absolute temp dir for given web server.
     *
     * @param prefix server name
     * @return the temp dir for given server.
     */
    protected final File createTempDir(String prefix) {
        try {
            File tempDir = Files.createTempDirectory(prefix + "." + serverConfig.getPort() + ".").toFile();
            tempDir.deleteOnExit();
            return tempDir;
        } catch (IOException e) {
            log.error("Unable to create tempDir, {}", e.getMessage());
            ThrowProvider.doThrow(e);
            return null;
        }
    }
}
