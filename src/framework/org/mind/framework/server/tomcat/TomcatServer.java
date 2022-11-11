package org.mind.framework.server.tomcat;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.util.ServerInfo;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.mind.framework.dispatcher.DispatcherServlet;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.exception.WebServerException;
import org.mind.framework.server.ServerContext;
import org.mind.framework.server.WebServerConfig;
import org.mind.framework.server.XmlLoad4SpringContext;
import org.mind.framework.service.ExecutorFactory;
import org.mind.framework.util.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.XmlWebApplicationContext;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;

/**
 * @author Marcus
 * @version 1.0
 */
public class TomcatServer extends Tomcat {
    private static final Logger log = LoggerFactory.getLogger(TomcatServer.class);
    private final WebServerConfig serverConfig;

    public TomcatServer(WebServerConfig serverConfig) {
        super();
        this.serverConfig = serverConfig;

        super.setPort(serverConfig.getPort());
        super.setBaseDir(serverConfig.getTomcatBaseDir());

        Connector connector = getNioConnector();
        super.getService().addConnector(connector);// getService auto create
        super.setConnector(connector);

        Host host = super.getHost();
        host.setAutoDeploy(false);
    }

    @Override
    public Context addWebapp(Host host, String contextPath, String docBase) {
        StandardContext ctx = new TomcatEmbeddedContext();
        ctx.setPath(contextPath);
        ctx.setDocBase(docBase);

        // by web.xml
        if (StringUtils.isNotEmpty(serverConfig.getWebXml())) {
            log.debug("Load Web-Server config: {}", serverConfig.getWebXml());
            ctx.addLifecycleListener(super.getDefaultWebXmlListener());
            LifecycleListener config = this.getContextListener(host);
            ctx.addLifecycleListener(config);

            if (config instanceof ContextConfig)
                ((ContextConfig) config).setDefaultWebXml(new File(serverConfig.getWebXml()).getAbsolutePath());
        } else {
            log.debug("Creation mind-framework servlet: [{}]", ServerContext.SERVLET_CLASS);

            // create servlet
            this.createServlet(host, ctx);

            // set mime-type
            this.serverConfig.getMimeMapping().forEach(ctx::addMimeMapping);

            // Add error | exception page
            ctx.addErrorPage(newErrorPage(400, "/error/400"));
            ctx.addErrorPage(newErrorPage(404, "/error/404"));
            ctx.addErrorPage(newErrorPage(500, "/error/500"));
            ctx.addErrorPage(newErrorPage("java.lang.NullPointerException", "/error/500"));
            ctx.addErrorPage(newErrorPage("javax.servlet.ServletException", "/error/500"));
            ctx.addErrorPage(newErrorPage("java.lang.Exception", "/error/500"));
        }

        host.addChild(ctx);
        return ctx;
    }

    protected Connector getNioConnector() {
        // org.apache.coyote.http11.Http11Nio2Protocol
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
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

    public void logServer() {
        ServerInfo.main(null);
        System.out.println("CATALINA_BASE:  " + System.getProperty("catalina.base"));
        System.out.println("CATALINA_HOME:  " + System.getProperty("catalina.home"));
        ManagementFactory.getRuntimeMXBean().getInputArguments().forEach(arg -> System.out.println("Command line argument: " + arg));
    }


    /**
     * Tomcat threads are daemon threads.
     * Create a blocking non-daemon to stop immediate shutdown
     */
    @Override
    public void start() {
        try {
            super.start();
            Thread awaitThread = ExecutorFactory.newThread(
                    "web-container-".concat(String.valueOf(serverConfig.getPort())),
                    () -> TomcatServer.this.getServer().await());
            awaitThread.setContextClassLoader(getClass().getClassLoader());
            awaitThread.start();
        } catch (Exception e) {
            stop();
            destroy();
            throw new WebServerException(e.getMessage(), e);
        }
    }

    public Context findContext() {
        Container[] containers = this.getHost().findChildren();
        for (Container child : containers)
            if (child instanceof Context)
                return (Context) child;

        throw new IllegalStateException("The host does not contain a Context");
    }

    /**
     * Stop Quietly
     */
    @Override
    public void stop() {
        try {
            super.stop();
        } catch (LifecycleException e) {
        }
    }

    /**
     * Destroy Quietly
     */
    @Override
    public void destroy() {
        try {
            super.destroy();
        } catch (LifecycleException e) {
        }

        log.debug("Delete tomcat temp directory ....");
        try {
            if (StringUtils.isNotEmpty(serverConfig.getTomcatBaseDir()))
                FileUtils.deleteDirectory(new File(serverConfig.getTomcatBaseDir()));
        } catch (IOException e) {
            log.warn("Failed to delete tomcat temp directory, {}", e.getMessage());
        }
    }

    private void createServlet(Host host, StandardContext ctx) {
        ctx.addLifecycleListener(new Tomcat.FixContextListener());
        ctx.addLifecycleListener(getContextListener(host));

        Wrapper wrapper = Tomcat.addServlet(ctx, ServerContext.SERVLET_NAME, new DispatcherServlet());

        wrapper.addInitParameter("container", serverConfig.getContainerAware());
        if (StringUtils.isNotEmpty(serverConfig.getTemplateEngine()))
            wrapper.addInitParameter("template", serverConfig.getTemplateEngine());

        wrapper.addInitParameter("resource", serverConfig.getStaticSuffix());
        wrapper.addInitParameter("expires", serverConfig.getResourceExpires());
        wrapper.setLoadOnStartup(1);

        ctx.setSessionTimeout(serverConfig.getSessionTimeout());
        ctx.addServletMappingDecoded("/", ServerContext.SERVLET_NAME);

        // Add Spring loader
        if (serverConfig.getSpringFileSet() == null || serverConfig.getSpringFileSet().isEmpty()) {
            log.warn("Spring's config file is not set.");
        } else {
            // init spring by xml, XmlLoad4SpringContext is custom implementation
            XmlWebApplicationContext xmas = new XmlLoad4SpringContext();

            // load spring config
            xmas.setConfigLocations(serverConfig.getSpringFileSet().toArray(new String[0]));

            // load properties
            loadResource(xmas.getEnvironment());

            // Listen when spring starts by ContextLoaderListener
            ctx.addApplicationLifecycleListener(new ContextLoaderListener(xmas));
        }
    }

    private void loadResource(ConfigurableEnvironment environment) {
        if (serverConfig.getResourceSet() == null || serverConfig.getResourceSet().isEmpty())
            return;

        environment.setPlaceholderPrefix(PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_PREFIX);
        environment.setPlaceholderSuffix(PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_SUFFIX);

        MutablePropertySources propertySources = environment.getPropertySources();
        serverConfig.getResourceSet().forEach(res -> {
            try {
                propertySources.addFirst(new ResourcePropertySource(new ClassPathResource(res)));
            } catch (IOException e) {
                ThrowProvider.doThrow(e);
            }
        });

        propertySources.forEach(res -> {
            if (res.getName().startsWith("class path resource"))
                log.info("Loading resource: [{}]", StringUtils.substringBetween(res.getName(), "[", "]"));
        });
    }

    private LifecycleListener getContextListener(Host host) {
        try {
            Class<?> clazz = ClassUtils.getClass(host.getConfigClass());
            return (LifecycleListener) clazz.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            ThrowProvider.doThrow(e);
        }
        return null;
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
