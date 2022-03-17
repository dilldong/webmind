package org.mind.framework.server;

import lombok.Builder;
import lombok.Setter;
import org.apache.catalina.Server;
import org.apache.catalina.WebResourceRoot;
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
import org.mind.framework.util.DateFormatUtils;
import org.mind.framework.util.PropertiesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Setter
    private String serverName = "Tomcat";
    @Setter
    private int port = 10030;
    @Setter
    private int connectionTimeout = 20_000;
    @Setter
    private int maxConnections = 1024;
    @Setter
    private int minSpareThreads = 5;
    @Setter
    private int maxThreads = 200;
    @Setter
    private int acceptCount = 100;
    @Setter
    private String baseDir;
    @Setter
    private boolean webApp = true;
    @Setter
    private String webXml;
    @Setter
    private String webContext = StringUtils.EMPTY;

    private List<DirResourceBuilder> resourceSetList;
    private Properties properties;

    public WebServer() {
        this.resourceSetList = new ArrayList<>();
        if (WebServer.class.getResource("/") != null)
            this.baseDir = WebServer.class.getResource("/").getPath();

        try {
            this.properties = PropertiesUtils.getProperties(WebServer.class.getResourceAsStream(SERVER_PROPERTIES));
        } catch (NullPointerException e) {
            this.properties = PropertiesUtils.getProperties(WebServer.class.getResourceAsStream(String.format("/config%s", SERVER_PROPERTIES)));
        }

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

        tomcat.getHost().setAutoDeploy(false);

        // 创建webapp
        tomcat.setBaseDir(baseDir);

        // addWebapp方法启动web项目
        StandardContext ctx = webApp ?
                (StandardContext) tomcat.addWebapp(webContext, baseDir)
                : (StandardContext) tomcat.addContext(webContext, baseDir);

        //addContext启动非web项目，没有webapp子类的文件夹，也不存在web.xml文件
//        StandardContext ctx = (StandardContext) tomcat.addContext("", baseDir);

        // 如通过addContext启动，为StandardContext添加Listener，
//        ctx.addLifecycleListener((LifecycleListener) Class.forName(tomcat.getHost().getConfigClass()).newInstance());

        if (webApp && StringUtils.isNotEmpty(webXml))
            ctx.setDefaultWebXml(String.format("%s%s", baseDir, webXml));

        tomcat.enableNaming();
        ctx.setAddWebinfClassesResources(true);

        WebResourceRoot resources = new StandardRoot(ctx);
        resourceSetList.forEach(resource ->
                resources.addPreResources(new DirResourceSet(resources, resource.webAppMount, resource.base, resource.internalPath))
        );
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

    @Builder
    private static class DirResourceBuilder {
        private String webAppMount;
        private String base;
        private String internalPath;
    }
}
