package org.mind.framework.server;

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
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Marcus
 * @version 1.1
 */
public class WebServer {
    private static final Logger log = LoggerFactory.getLogger(WebServer.class);
    private static final String SERVLET_NAME = "mindDispatcher";
    private static final String SERVLET_CLASS = "org.mind.framework.dispatcher.DispatcherServlet";

    private volatile Set<String> springFileSet;
    private volatile Set<String> resourceSet;
    private WebServerConfig serverConfig;

    public WebServer() {
        this.serverConfig = WebServerConfig.init();
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
        tomcat.addWebapp(host, serverConfig.getContextPath(), createTempDir(serverConfig.getServerName()).getAbsolutePath());

        // Prevent memory leaks due to use of particular java/javax APIs
        Server server = tomcat.getServer();
        server.addLifecycleListener(new JreMemoryLeakPreventionListener());
        server.addLifecycleListener(new GlobalResourcesLifecycleListener());
        server.addLifecycleListener(new ThreadLocalLeakPreventionListener());

        // 监听tomcat停止, 建议用kill -15 优雅的停止服务
        GracefulShutdown shutdown = new GracefulShutdown(Thread.currentThread(), tomcat);
        shutdown.registerShutdownHook();

        tomcat.start();
        log.info("Starting Protocol: [{}], [{}]",
                serverConfig.getPort(),
                StringUtils.isEmpty(serverConfig.getContextPath()) ? "/" : serverConfig.getContextPath());

        log.info("{} startup time: {}ms", serverConfig.getServerName(), (DateFormatUtils.getTimeMillis() - begin));
        server.await();
    }


    public WebServer addSpringFile(String... filePath) {
        if (filePath == null || filePath.length == 0)
            return this;

        if (this.springFileSet == null)
            this.springFileSet = new HashSet<>();

        this.springFileSet.addAll(Arrays.asList(filePath));
        return this;
    }

    public WebServer addResource(String... resPath) {
        if (resPath == null || resPath.length == 0)
            return this;

        if (this.resourceSet == null)
            this.resourceSet = new HashSet<>();

        this.resourceSet.addAll(Arrays.asList(resPath));
        return this;
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

    protected Connector getNioConnector() {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");//org.apache.coyote.http11.Http11Nio2Protocol
        connector.setUseBodyEncodingForURI(true);
        connector.setThrowOnFailure(true);

        Http11NioProtocol nioProtocol = (Http11NioProtocol) connector.getProtocolHandler();
        nioProtocol.setPort(serverConfig.getPort());
        nioProtocol.setConnectionTimeout(serverConfig.getConnectionTimeout());
        nioProtocol.setCompression("on");
        nioProtocol.setMaxThreads(serverConfig.getMaxThreads());
        nioProtocol.setAcceptCount(serverConfig.getAcceptCount());
        nioProtocol.setMaxConnections(serverConfig.getMaxConnections());
        nioProtocol.setMinSpareThreads(serverConfig.getMinSpareThreads());
        return connector;
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
            if (StringUtils.isNotEmpty(serverConfig.getWebXml())) {
                log.info("Load Web-Server config: {}", serverConfig.getWebXml());
                ctx.addLifecycleListener(this.getDefaultWebXmlListener());
                LifecycleListener config = this.getContextListener(host);
                ctx.addLifecycleListener(config);

                if (config instanceof ContextConfig)
                    ((ContextConfig) config).setDefaultWebXml(new File(serverConfig.getWebXml()).getAbsolutePath());
            } else {
                log.info("Creation mindframework servlet: [{}]", SERVLET_CLASS);
                createServlet(host, ctx);// create servlet
            }

            host.addChild(ctx);
            return ctx;
        }

        private void createServlet(Host host, StandardContext ctx) {
            ctx.addLifecycleListener(new Tomcat.FixContextListener());
            ctx.addLifecycleListener(getContextListener(host));

            Wrapper wrapper = Tomcat.addServlet(ctx, SERVLET_NAME, SERVLET_CLASS);

            wrapper.addInitParameter("container", serverConfig.getContainerAware());
            if (StringUtils.isNotEmpty(serverConfig.getTemplateEngine()))
                wrapper.addInitParameter("template", serverConfig.getTemplateEngine());

            wrapper.addInitParameter("resource", serverConfig.getStaticSuffix());
            wrapper.addInitParameter("expires", serverConfig.getResourceExpires());
            wrapper.setLoadOnStartup(1);

            ctx.setSessionTimeout(serverConfig.getSessionTimeout());
            ctx.addServletMappingDecoded("/", SERVLET_NAME);

            // Add Spring loader
            if (springFileSet == null || springFileSet.isEmpty()) {
                log.warn("Spring's config file is not set.");
            } else {
                XmlWebApplicationContext xmas = new XmlLoad4SpringContext();
                xmas.setConfigLocations(springFileSet.toArray(new String[springFileSet.size()]));// load spring config
                loadResource(xmas.getEnvironment());// load properties
                ctx.addApplicationLifecycleListener(new ContextLoaderListener(xmas));
            }

            // Add error | exception page
            ctx.addErrorPage(newErrorPage(400, "/error/400"));
            ctx.addErrorPage(newErrorPage(404, "/error/404"));
            ctx.addErrorPage(newErrorPage(500, "/error/500"));
            ctx.addErrorPage(newErrorPage("java.lang.NullPointerException", "/error/199"));
            ctx.addErrorPage(newErrorPage("javax.servlet.ServletException", "/error/199"));
            ctx.addErrorPage(newErrorPage("java.lang.Exception", "/error/199"));
        }

        private void loadResource(ConfigurableEnvironment environment) {
            if (resourceSet == null || resourceSet.isEmpty())
                return;

            environment.setPlaceholderPrefix(PropertyPlaceholderConfigurer.DEFAULT_PLACEHOLDER_PREFIX);
            environment.setPlaceholderSuffix(PropertyPlaceholderConfigurer.DEFAULT_PLACEHOLDER_SUFFIX);

            MutablePropertySources propertySources = environment.getPropertySources();
            resourceSet.forEach(res -> {
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
