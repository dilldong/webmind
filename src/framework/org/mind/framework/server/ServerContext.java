package org.mind.framework.server;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Server;
import org.apache.catalina.core.JreMemoryLeakPreventionListener;
import org.apache.catalina.core.ThreadLocalLeakPreventionListener;
import org.apache.catalina.mbeans.GlobalResourcesLifecycleListener;
import org.apache.catalina.startup.Tomcat;
import org.apache.commons.lang.StringUtils;
import org.mind.framework.exception.NotSupportedException;
import org.mind.framework.util.DateFormatUtils;

import java.io.File;
import java.io.IOException;
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

    private volatile Set<String> springFileSet;
    private volatile Set<String> resourceSet;

    protected WebServerConfig serverConfig;

    /**
     * Register tomcat-server you custom
     *
     * @param tomcat
     * @throws LifecycleException
     */
    protected abstract void registerServer(Tomcat tomcat) throws LifecycleException;

    public ServerContext() {
        this.serverConfig = WebServerConfig.init();
    }

    public void startup() throws LifecycleException {
        final long begin = DateFormatUtils.getTimeMillis();

        Tomcat tomcat = creationServer();
        this.registerServer(tomcat);

        // Prevent memory leaks due to use of particular java/javax APIs
        Server server = tomcat.getServer();
        server.addLifecycleListener(new JreMemoryLeakPreventionListener());
        server.addLifecycleListener(new GlobalResourcesLifecycleListener());
        server.addLifecycleListener(new ThreadLocalLeakPreventionListener());

        tomcat.start();
        log.info("Starting Protocol: [{}], [{}]",
                serverConfig.getPort(),
                StringUtils.isEmpty(serverConfig.getContextPath()) ? "/" : serverConfig.getContextPath());

        log.info("{} startup time: {}ms", serverConfig.getServerName(), (DateFormatUtils.getTimeMillis() - begin));
        server.await();
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

        TomcatServer tomcat = new TomcatServer(serverConfig);
        // logging server
        tomcat.logServer();

        return tomcat;
    }

    protected File createTempDir(String prefix) {
        try {
            File tempDir = File.createTempFile(prefix + ".", "." + serverConfig.getPort());
            tempDir.delete();
            tempDir.mkdir();
            tempDir.deleteOnExit();
            return tempDir;
        } catch (IOException e) {
            log.error("Unable to create tempDir, {}", e.getMessage());
            throw new NotSupportedException(e.getMessage(), e);
        }
    }
}
