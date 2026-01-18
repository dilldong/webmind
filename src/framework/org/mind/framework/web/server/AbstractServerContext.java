package org.mind.framework.web.server;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.Server;
import org.apache.catalina.core.JreMemoryLeakPreventionListener;
import org.apache.catalina.core.ThreadLocalLeakPreventionListener;
import org.apache.catalina.mbeans.GlobalResourcesLifecycleListener;
import org.apache.catalina.startup.Tomcat;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.exception.WebServerException;
import org.mind.framework.util.IOUtils;
import org.mind.framework.web.server.tomcat.TomcatServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Marcus
 * @version 1.0
 */
public abstract class AbstractServerContext {
    static final Logger log = LoggerFactory.getLogger(AbstractServerContext.class);
    public static final String SERVLET_NAME = "webmindServlet";

    private transient Set<String> springFileSet;
    private transient Set<String> resourceSet;
    private transient Set<Class<?>> configClassSet;

    private final WebServerConfig serverConfig;

    private final Object monitor = new Object();

    /**
     * Register tomcat-server you custom
     *
     * @throws LifecycleException
     */
    protected abstract void registerServer(Tomcat tomcat, WebServerConfig serverConfig) throws LifecycleException;

    public AbstractServerContext() {
        this.serverConfig = WebServerConfig.INSTANCE.initMimeMapping();
    }

    public void startup() throws WebServerException {
        synchronized (this.monitor) {
            StopWatch stopWatch = StopWatch.createStarted();
            try {
                Tomcat tomcat = creationServer();
                this.registerServer(tomcat, serverConfig);

                // use JNDI service
                tomcat.enableNaming();

                // Prevent memory leaks due to use of particular java/javax APIs
                Server server = tomcat.getServer();
                server.addLifecycleListener(new JreMemoryLeakPreventionListener());
                server.addLifecycleListener(new GlobalResourcesLifecycleListener());
                server.addLifecycleListener(new ThreadLocalLeakPreventionListener());

                // Unlike Jetty, all Tomcat threads are daemon threads. We create a
                // blocking non-daemon to stop immediate shutdown
                tomcat.start();

                log.info("Starting {} Protocol: [{}], [{}]",
                        serverConfig.getNioMode().toUpperCase(),
                        serverConfig.getPort(),
                        StringUtils.isEmpty(serverConfig.getContextPath()) ? IOUtils.DIR_SEPARATOR : serverConfig.getContextPath());

                log.info("{} startup time: {}ms", serverConfig.getServerName(), stopWatch.getTime(TimeUnit.MILLISECONDS));
            } catch (Exception e) {
                throw new WebServerException("Unable to start embedded Tomcat", e);
            } finally {
                stopWatch.stop();
            }
        }
    }

    public AbstractServerContext addConfigClass(Class<?> configClass){
        if (Objects.isNull(configClass))
            return this;

        if (Objects.isNull(this.configClassSet))
            this.configClassSet = new HashSet<>();

        this.configClassSet.add(configClass);
        return this;
    }

    public AbstractServerContext addSpringFile(String... filePath) {
        if (ArrayUtils.isEmpty(filePath))
            return this;

        if (Objects.isNull(this.springFileSet))
            this.springFileSet = new HashSet<>();

        this.springFileSet.addAll(List.of(filePath));
        return this;
    }

    public AbstractServerContext addResource(String... resPath) {
        if (ArrayUtils.isEmpty(resPath))
            return this;

        if (Objects.isNull(this.resourceSet))
            this.resourceSet = new HashSet<>();

        this.resourceSet.addAll(List.of(resPath));
        return this;
    }

    protected Tomcat creationServer() {
        serverConfig.setResourceSet(resourceSet);
        serverConfig.setSpringFileSet(springFileSet);
        serverConfig.setSpringConfigClassSet(configClassSet);

        // Set Tomcat base-work-directory
        File baseDir;
        if(StringUtils.isEmpty(serverConfig.getTomcatBaseDir())) {
            baseDir = createTempDir(serverConfig.getServerName());
        } else {
            // Not recommended
            Path path = Paths.get(serverConfig.getTomcatBaseDir());
            if(Files.notExists(path)){
                try {
                    Files.createDirectories(path);
                } catch (IOException ignored) {}
            }
            baseDir = path.toFile();
        }

        Objects.requireNonNull(baseDir, "The tomcat work directory not exists.");
        serverConfig.setTomcatBaseDir(baseDir.getAbsolutePath());
        ResourcePatternResolver patternResolver = new PathMatchingResourcePatternResolver();

        // Copy root files
        if(StringUtils.isNotEmpty(serverConfig.getResourceRootFiles())){
            String[] paths = StringUtils.split(serverConfig.getResourceRootFiles(), ',');
            for(String path : paths){
                try {
                    Resource[] resources = patternResolver.getResources(String.format("classpath*:/%s", path.trim()));
                    if(ArrayUtils.isEmpty(resources))
                        continue;

                    for (Resource resource : resources) {
                        log.debug("Copy resource to baseDir: {}", resource.getFilename());
                        FileUtils.copyInputStreamToFile(
                                resource.getInputStream(),
                                new File(String.format("%s/%s", serverConfig.getTomcatBaseDir(), resource.getFilename())));
                    }
                } catch (IOException e) {
                    throw new WebServerException(e.getMessage(), e);
                }
            }
        }

        // Copy web static resources
        if (StringUtils.isNotEmpty(serverConfig.getResourceDir())) {
            String resNamePattern = String.format("classpath*:/%s/**/*.*", serverConfig.getResourceDir());

            try {
                Resource[] resources = patternResolver.getResources(resNamePattern);
                if (ArrayUtils.isNotEmpty(resources)) {
                    String resourceBaseDir =
                            String.format("%s/%s",
                                    serverConfig.getTomcatBaseDir(),
                                    serverConfig.getResourceDir().startsWith(IOUtils.DIR_SEPARATOR) ?
                                            serverConfig.getResourceDir().substring(1) :
                                            serverConfig.getResourceDir());

                    for (Resource resource : resources) {
                        String namePath = StringUtils.substringAfter(
                                resource.getURL().getPath(), serverConfig.getResourceDir());

                        log.debug("Copy static resource: {}{}", serverConfig.getResourceDir(), namePath);
                        FileUtils.copyInputStreamToFile(
                                resource.getInputStream(),
                                new File(String.format("%s%s", resourceBaseDir, namePath)));
                    }
                } else
                    log.warn("Static resource directory does not exist: '{}'", serverConfig.getResourceDir());
            } catch (IOException e) {
                throw new WebServerException(e.getMessage(), e);
            }
        }

        // Create Tomcat-Server
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
            File tempDir = Files.createTempDirectory(
                    String.format("%s.%d.", prefix, serverConfig.getPort())).toFile();
            tempDir.deleteOnExit();
            return tempDir;
        } catch (IOException e) {
            log.error("Unable to create tempDir, {}", e.getMessage());
            ThrowProvider.doThrow(e);
        }

        return null;
    }
}
