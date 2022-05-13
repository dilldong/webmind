package org.mind.framework.server;

import lombok.Getter;
import lombok.Setter;
import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.JreMemoryLeakPreventionListener;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.ThreadLocalLeakPreventionListener;
import org.apache.catalina.mbeans.GlobalResourcesLifecycleListener;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.util.ServerInfo;
import org.apache.commons.lang.StringUtils;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.mind.framework.exception.NotSupportedException;
import org.mind.framework.util.DateFormatUtils;
import org.mind.framework.util.JarFileUtils;
import org.mind.framework.util.PropertiesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.XmlWebApplicationContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
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
    private static final String SERVLET_NAME = "mindDispatcher";
    private static final String SERVLET_CLASS = "org.mind.framework.dispatcher.DispatcherServlet";

    public static final String JAR_IN_CLASSES = "BOOT-INF/classes";
    private static final String SERVER_PROPERTIES = "/server.properties";
    private static final String JAR_PROPERTIES = String.format("%s%s", JAR_IN_CLASSES, SERVER_PROPERTIES);

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

    @Getter
    @Setter
    private String contextPath = StringUtils.EMPTY;

    @Setter
    @Getter
    private String webXml;

    private volatile List<String> springFileList;
    private volatile List<String> resourceList;

    public WebServer() throws FileNotFoundException {
        this.initServer();
    }

    public void startServer() throws LifecycleException {
        final long begin = DateFormatUtils.getTimeMillis();

        Tomcat tomcat = new TomcatServer();
        Connector connector = getNioConnector();
        tomcat.getService().addConnector(connector);// getService auto create
        tomcat.setConnector(connector);

        Host host = tomcat.getHost();
        host.setAutoDeploy(false);

        // use JNDI sevice
        tomcat.enableNaming();

        // logging server
        this.logServer();

        // add webapp
        tomcat.addWebapp(host, contextPath, createTempDir("Tomcat").getAbsolutePath());

        // Prevent memory leaks due to use of particular java/javax APIs
        Server server = tomcat.getServer();
        server.addLifecycleListener(new JreMemoryLeakPreventionListener());
        server.addLifecycleListener(new GlobalResourcesLifecycleListener());
        server.addLifecycleListener(new ThreadLocalLeakPreventionListener());

        // 监听tomcat停止, 建议用kill -15 优雅的停止服务
        GracefulShutdown shutdown = new GracefulShutdown(Thread.currentThread(), tomcat);
        shutdown.registerShutdownHook();

        tomcat.start();
        log.info("{} startup time: {}ms", serverName, (DateFormatUtils.getTimeMillis() - begin));
        server.await();
    }


    public WebServer addSpringFile(String filePath) {
        if (StringUtils.isEmpty(filePath))
            return this;

        if (springFileList == null)
            springFileList = new ArrayList<>();

        this.springFileList.add(filePath);
        return this;
    }

    public WebServer addSpringFile(String... filePath) {
        if (filePath == null || filePath.length == 0)
            return this;

        for (String file : filePath)
            this.addSpringFile(file);

        return this;
    }

    public WebServer addResource(String resPath) {
        if (StringUtils.isEmpty(resPath))
            return this;

        if (resourceList == null)
            resourceList = new ArrayList<>();

        this.resourceList.add(resPath);
        return this;
    }

    public WebServer addResource(String... resPath) {
        if (resPath == null || resPath.length == 0)
            return this;

        for (String file : resPath)
            this.addResource(file);

        return this;
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

    protected Connector getNioConnector() {
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

    private void initServer() throws FileNotFoundException {
        InputStream in;
        URL url = WebServer.class.getResource(SERVER_PROPERTIES);

        try {
            if (url != null)
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

    private void initSpringConfig() throws FileNotFoundException {
        if (springFileList != null && !springFileList.isEmpty())
            return;
    }

    private void logServer() {
        ServerInfo.main(null);
        System.out.println("CATALINA_BASE:  " + System.getProperty("catalina.base"));
        System.out.println("CATALINA_HOME:  " + System.getProperty("catalina.home"));
        ManagementFactory.getRuntimeMXBean().getInputArguments().forEach(arg -> System.out.println("Command line argument: " + arg));
    }

    class TomcatServer extends Tomcat {
        LifecycleListener getContextListener(Host host) {
            try {
                Class<?> clazz = Class.forName(host.getConfigClass());
                return (LifecycleListener) clazz.getConstructor().newInstance();
            } catch (ReflectiveOperationException ex) {
                throw new IllegalArgumentException(ex);
            }
        }

        @Override
        public Context addWebapp(Host host, String contextPath, String docBase) {
            StandardContext ctx = new StandardContext();
            ctx.setPath(contextPath);
            ctx.setDocBase(docBase);

            // by web.xml
            if (StringUtils.isNotEmpty(webXml)) {
                log.info("Load Web-Server config: {}", webXml);
                ctx.addLifecycleListener(this.getDefaultWebXmlListener());
                LifecycleListener config = this.getContextListener(host);
                ctx.addLifecycleListener(config);

                if (config instanceof ContextConfig)
                    ((ContextConfig) config).setDefaultWebXml(new File(webXml).getAbsolutePath());
            } else {
                log.info("Creation servlet: [{}]", SERVLET_CLASS);
                createServlet(host, ctx);// create servlet
            }

            host.addChild(ctx);
            return ctx;
        }

        private void createServlet(Host host, StandardContext ctx) {
            ctx.addLifecycleListener(new Tomcat.FixContextListener());
            ctx.addLifecycleListener(getContextListener(host));

            Wrapper wrapper = Tomcat.addServlet(ctx, SERVLET_NAME, SERVLET_CLASS);

            wrapper.addInitParameter("container", "Spring");
            wrapper.addInitParameter("template", "Velocity");
            wrapper.addInitParameter("resource", "css|js|jpg|png|gif|html|htm|xls|xlsx|doc|docx|ppt|pptx|pdf|rar|zip|txt");
            wrapper.addInitParameter("expires", "-1");
            wrapper.setLoadOnStartup(1);

            ctx.setSessionTimeout(30);
            ctx.addServletMappingDecoded("/", SERVLET_NAME);

            // Add Spring loader
            if (springFileList == null || springFileList.isEmpty()) {
                log.warn("Spring's config file is not set.");
            } else {
                XmlWebApplicationContext xmas = new XmlLoadForSpringContext();
                xmas.setConfigLocations(springFileList.toArray(new String[springFileList.size()]));// load spring config
                //loadResource(xmas.getEnvironment());// load properties
                ctx.addApplicationLifecycleListener(new ContextLoaderListener(xmas));
            }

            // Add error | exception page
            ctx.addErrorPage(newErrorPage(400, "/error/400"));
            ctx.addErrorPage(newErrorPage(404, "/error/404"));
            ctx.addErrorPage(newErrorPage(500, "/error/500"));
            ctx.addErrorPage(newErrorPage("java.lang.NullPointerException", "/error/NullPointerException"));
            ctx.addErrorPage(newErrorPage("javax.servlet.ServletException", "/error/ServletException"));
            ctx.addErrorPage(newErrorPage("java.lang.Exception", "/error/Exception"));
        }

        private void loadResource(ConfigurableEnvironment environment) {
            if (resourceList == null || resourceList.isEmpty())
                return;

            environment.setPlaceholderPrefix(PropertyPlaceholderConfigurer.DEFAULT_PLACEHOLDER_PREFIX);
            environment.setPlaceholderSuffix(PropertyPlaceholderConfigurer.DEFAULT_PLACEHOLDER_SUFFIX);

            MutablePropertySources propertySources = environment.getPropertySources();
            resourceList.forEach(res -> {
                try {
                    propertySources.addFirst(new ResourcePropertySource(new ClassPathResource(res)));
                } catch (IOException e) {
                }
            });

            propertySources.forEach(res -> {
                if (res.getName().startsWith("class path resource"))
                    log.info("Loading resource: [{}]", StringUtils.substringBetween(res.getName(), "[", "]"));
            });
        }

        private ErrorPage newErrorPage(int errorCode, String location) {
            ErrorPage errorPage = new ErrorPage();
            errorPage.setErrorCode(errorCode);
            errorPage.setLocation(location);
            return errorPage;
        }

        private ErrorPage newErrorPage(String exceptionType, String location) {
            ErrorPage errorPage = new ErrorPage();
            errorPage.setExceptionType(exceptionType);
            errorPage.setLocation(location);
            return errorPage;
        }
    }
}
