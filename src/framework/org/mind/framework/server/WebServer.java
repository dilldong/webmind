package org.mind.framework.server;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.JreMemoryLeakPreventionListener;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.ThreadLocalLeakPreventionListener;
import org.apache.catalina.mbeans.GlobalResourcesLifecycleListener;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.commons.lang.StringUtils;
import org.apache.coyote.http11.Http11NioProtocol;
import org.mind.framework.exception.NotSupportedException;
import org.mind.framework.util.DateFormatUtils;
import org.mind.framework.util.JarFileUtils;
import org.mind.framework.util.PropertiesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author Marcus
 * @version 1.0
 * @date 2022-03-14
 */
public class WebServer {
    private static final Logger log = LoggerFactory.getLogger(WebServer.class);
    private static final String SERVER_PROPERTIES = "/server.properties";
    private static final String JAR_PROPERTIES = "BOOT-INF/classes/server.properties";

    @Setter
    @Getter
    private String serverName = "Tomcat";

    @Setter
    @Getter
    private int port = 10030;

    @Setter
    @Getter
    private int connectionTimeout = 20_000;

    @Setter
    @Getter
    private int maxConnections = 1024;

    @Setter
    @Getter
    private int minSpareThreads = 5;

    @Setter
    @Getter
    private int maxThreads = 200;

    @Getter
    @Setter
    private int acceptCount = 100;

    @Setter
    @Getter
    private String baseDir;

    @Getter
    @Setter
    private boolean webApp = true;

    @Getter
    @Setter
    private String webXml;

    @Getter
    @Setter
    private boolean addWebinfClassesResources;

    @Getter
    @Setter
    private String webContext = StringUtils.EMPTY;

    private List<DirResourceBuilder> resourceSetList;

    @SneakyThrows
    public WebServer() {
        this.resourceSetList = new ArrayList<>();
        final String runtimePath = JarFileUtils.getRuntimePath();
        baseDir = runtimePath;

        InputStream in;
        URL url = WebServer.class.getResource(SERVER_PROPERTIES);

        try {
            if (url == null)
                in = WebServer.class.getResourceAsStream(SERVER_PROPERTIES);
            else
                in = JarFileUtils.getJarEntryStream(JAR_PROPERTIES);
        } catch (Exception e) {
            throw new FileNotFoundException("Not found 'server.properties'");
        }

        Properties properties = PropertiesUtils.getProperties(in);
        if (properties != null) {
            this.serverName = properties.getProperty("server", serverName);
            this.port = Integer.parseInt(properties.getProperty("server.port", String.valueOf(port)));
            this.connectionTimeout = Integer.parseInt(properties.getProperty("server.connectionTimeout", String.valueOf(connectionTimeout)));
            this.maxConnections = Integer.parseInt(properties.getProperty("server.maxConnections", String.valueOf(maxConnections)));
            this.maxThreads = Integer.parseInt(properties.getProperty("server.maxThreads", String.valueOf(maxThreads)));
            this.minSpareThreads = Integer.parseInt(properties.getProperty("server.minSpareThreads", String.valueOf(minSpareThreads)));
            this.acceptCount = Integer.parseInt(properties.getProperty("server.acceptCount", String.valueOf(acceptCount)));
        }
    }


    public WebServer addPreResources(String webAppMount, String base, String internalPath) {
        this.resourceSetList.add(
                DirResourceBuilder.builder()
                        .webAppMount(webAppMount)
                        .base(base)
                        .internalPath(internalPath)
                        .build());
        return this;
    }


    public void startServer() throws Exception {
        final long begin = DateFormatUtils.getTimeMillis();

        Tomcat tomcat = new Tomcat();
        Connector connector = getNioConnector();
        tomcat.getService().addConnector(connector);// getService auto create
        tomcat.setConnector(connector);

        Host host = tomcat.getHost();
        host.setAutoDeploy(false);

        // 创建webapp
        tomcat.setBaseDir(baseDir);

        // 使用JNDI服务
        tomcat.enableNaming();

        // addWebapp方法启动web项目
        StandardContext ctx = webApp ?
                (StandardContext) tomcat.addWebapp(host, webContext, baseDir)
                : (StandardContext) tomcat.addContext(host, webContext, baseDir);

        ctx.setAddWebinfClassesResources(addWebinfClassesResources);

        try {
            ctx.setUseRelativeRedirects(false);
        } catch (NoSuchMethodError e) {
        }

        //addContext启动非web项目，没有webapp子类的文件夹，也不存在web.xml文件
//        StandardContext ctx = (StandardContext) tomcat.addContext(webContext, baseDir);

        // 如通过addContext启动，为StandardContext添加Listener，
        ctx.addLifecycleListener((LifecycleListener) Class.forName(tomcat.getHost().getConfigClass()).newInstance());

        // 设置默认的web.xml
        if (webApp && StringUtils.isNotEmpty(webXml))
            ctx.setDefaultWebXml(webXml);

        StandardRoot resources = new StandardRoot(ctx);
        resourceSetList.forEach(resource ->
                resources.addPreResources(new DirResourceSet(resources, resource.webAppMount, resource.base, resource.internalPath))
        );
        resourceSetList.clear();

        ctx.setResources(resources);

        Server server = tomcat.getServer();

        // Prevent memory leaks due to use of particular java/javax APIs
        server.addLifecycleListener(new JreMemoryLeakPreventionListener());
        server.addLifecycleListener(new GlobalResourcesLifecycleListener());
        server.addLifecycleListener(new ThreadLocalLeakPreventionListener());

        // 监听tomcat停止, kill -9 无法监听到，建议用kill -15 优雅的停止服务
        GracefulShutdown shutdown = new GracefulShutdown(Thread.currentThread(), tomcat);
        shutdown.registerShutdownHook();

        tomcat.start();
        log.info("{} startup time: {}ms", serverName, (DateFormatUtils.getTimeMillis() - begin));
        server.await();
    }

    private Connector getNioConnector() {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");//org.apache.coyote.http11.Http11Nio2Protocol
        connector.setUseBodyEncodingForURI(true);
        connector.setThrowOnFailure(true);

        Http11NioProtocol nioProtocol = (Http11NioProtocol) connector.getProtocolHandler();
        nioProtocol.setPort(port);
        nioProtocol.setConnectionTimeout(connectionTimeout);
        nioProtocol.setCompression("on");
        nioProtocol.setMaxThreads(maxThreads);
        nioProtocol.setAcceptCount(acceptCount);
        nioProtocol.setMaxConnections(maxConnections);
        nioProtocol.setMinSpareThreads(minSpareThreads);
        return connector;
    }

    protected File createTempDir(String prefix) {
        try {
            File tempDir = File.createTempFile(prefix + ".", "." + this.getPort());
            tempDir.delete();
            tempDir.mkdir();
            tempDir.deleteOnExit();
            return tempDir;
        } catch (IOException e) {
            log.error("Unable to create tempDir", e);
            throw new NotSupportedException(e.getMessage(), e);
        }
    }

    @Builder
    private static class DirResourceBuilder {
        private String webAppMount;
        private String base;
        private String internalPath;
    }
}
