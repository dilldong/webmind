package org.mind.framework.web.server.tomcat;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Manager;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.util.ServerInfo;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.coyote.http11.AbstractHttp11JsseProtocol;
import org.apache.coyote.http2.Http2Protocol;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.mind.framework.ContextSupport;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.exception.WebServerException;
import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.util.IOUtils;
import org.mind.framework.web.container.spring.WebContextLoadListener;
import org.mind.framework.web.dispatcher.DispatcherServlet;
import org.mind.framework.web.server.ServerContext;
import org.mind.framework.web.server.WebServerConfig;
import org.mind.framework.web.server.XmlLoad4SpringContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

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
            if (log.isDebugEnabled())
                log.debug("Load Web-Server config: [{}]", serverConfig.getWebXml());

            ctx.addLifecycleListener(super.getDefaultWebXmlListener());
            LifecycleListener config = this.getContextListener(host);
            ctx.addLifecycleListener(config);

            if (config instanceof ContextConfig)
                ((ContextConfig) config).setDefaultWebXml(new File(serverConfig.getWebXml()).getAbsolutePath());
        } else {
            if (log.isDebugEnabled())
                log.debug("Creation default servlet: [{}]", DispatcherServlet.class.getName());

            // Allow parsing multipartform-data in non-POST requests
            ctx.setAllowCasualMultipartParsing(true);

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
            ctx.addErrorPage(newErrorPage("java.lang.RuntimeException", "/error/500"));
            ctx.addErrorPage(newErrorPage("java.lang.Exception", "/error/500"));
        }

        /*
            往 Servlet 容器的请求处理管道（pipeline）中添加一个 Valve（阀门）。
            org.apache.catalina.Valve 是 Tomcat 中用于处理请求的一个拦截器，
            如: 日志记录器、IP 黑白名单过滤、请求头处理、安全性增强、性能监控和调优。
         */
        // ctx.getPipeline().addValve();
        // super.getEngine().getPipeline().addValve();

        /*
         * Disable persistence in the StandardManager.
         * a LifecycleListener is used so not to interfere with Tomcat's default manager creation logic.
         */
        ctx.addLifecycleListener(event -> {
            if (event.getType().equals(Lifecycle.START_EVENT)) {
                Context context = (Context) event.getLifecycle();
                Manager manager = context.getManager();
                if (manager instanceof StandardManager) {
                    ((StandardManager) manager).setPathname(null);
                }
            }
        });

        host.addChild(ctx);
        return ctx;
    }

    protected Connector getNioConnector() {
        Connector connector;
        if ("nio2".equalsIgnoreCase(serverConfig.getNioMode()))
            connector = new Connector("org.apache.coyote.http11.Http11Nio2Protocol");
        else
            connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");

        connector.setUseBodyEncodingForURI(true);
        connector.setThrowOnFailure(true);
        connector.setMaxParameterCount(serverConfig.getMaxParameterCount());
        connector.setMaxPostSize(serverConfig.getMaxPostSize());

        AbstractHttp11JsseProtocol<?> nioProtocol = (AbstractHttp11JsseProtocol<?>) connector.getProtocolHandler();
        nioProtocol.setPort(Math.max(serverConfig.getPort(), 0));
        if (StringUtils.isNotEmpty(serverConfig.getServerName()))
            connector.setProperty("server", serverConfig.getServerName());

        if(Objects.nonNull(serverConfig.getBindAddress()))
            nioProtocol.setAddress(serverConfig.getBindAddress());

        // Don't bind to the socket prematurely if ApplicationContext is slow to start
        connector.setProperty("bindOnInit", "false");

        if(serverConfig.isHttp2Enabled())
            nioProtocol.addUpgradeProtocol(new Http2Protocol());

        // config SSL, As: TomcatServletWebServerFactory

        nioProtocol.setConnectionTimeout(serverConfig.getConnectionTimeout());

        if ("on".equalsIgnoreCase(serverConfig.getCompression())) {
            nioProtocol.setCompression("on");
            nioProtocol.setCompressionMinSize(serverConfig.getCompressionMinSize());
            nioProtocol.setCompressibleMimeType(serverConfig.getCompressibleMimeType());
            // compression为on时, 需要与sendfile互斥, 两者取其一
            nioProtocol.setUseSendfile(false);
        }

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
        Thread awaitThread = null;
        try {
            super.start();
            awaitThread = ExecutorFactory.newDaemonThread(
                    "tomcat-await-" + serverConfig.getPort(),
                    () -> TomcatServer.this.getServer().await());
            awaitThread.setContextClassLoader(getClass().getClassLoader());
            awaitThread.start();
        } catch (Exception e) {
            stop();
            destroy();
            if (Objects.nonNull(awaitThread) && awaitThread.isAlive())
                awaitThread.interrupt();
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
        } catch (LifecycleException ignored) {
        }
    }

    /**
     * Destroy Quietly
     */
    @Override
    public void destroy() {
        try {
            super.destroy();
        } catch (LifecycleException ignored) {
        }

        if (log.isDebugEnabled())
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
        ctx.addServletMappingDecoded(IOUtils.DIR_SEPARATOR, ServerContext.SERVLET_NAME);

        if (StringUtils.isNotEmpty(serverConfig.getTldSkipPatterns()))
            System.setProperty("tomcat.util.scan.StandardJarScanFilter.jarsToSkip", serverConfig.getTldSkipPatterns());

        // Add Spring loader
        if (Objects.isNull(serverConfig.getSpringFileSet()) || serverConfig.getSpringFileSet().isEmpty()) {
            if (Objects.nonNull(serverConfig.getSpringConfigClassSet()) && !serverConfig.getSpringConfigClassSet().isEmpty()) {
                AnnotationConfigWebApplicationContext ac = new AnnotationConfigWebApplicationContext();
                ac.register(serverConfig.getSpringConfigClassSet().toArray(new Class[0]));
                // by web-container destroy
                // @see SpringContainerAware.destroy()
                // ac.registerShutdownHook();

                // Listen when spring starts by ContextLoaderListener
                ctx.addApplicationLifecycleListener(new WebContextLoadListener(ac, ctx));

                // setting spring context
                ContextSupport.setApplicationContext(ac);
            } else
                log.warn("Spring's config class or file is not found.");
        } else {
            // init spring by xml, XmlLoad4SpringContext is custom implementation
            XmlWebApplicationContext xmas = new XmlLoad4SpringContext();

            // load spring config
            xmas.setConfigLocations(serverConfig.getSpringFileSet().toArray(new String[0]));

            // load properties in spring
            loadResource(xmas.getEnvironment());

            // by web-container destroy
            // @see SpringContainerAware.destroy()
            // xmas.registerShutdownHook();

            // Listen when spring starts by ContextLoaderListener
            ctx.addApplicationLifecycleListener(new WebContextLoadListener(xmas, ctx));

            // setting spring context
            ContextSupport.setApplicationContext(xmas);
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
                throw new WebServerException(e.getMessage(), e);
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
        errorPage.setCharset(StandardCharsets.UTF_8);
        return errorPage;
    }

    private ErrorPage newErrorPage(String exceptionType, String location) {
        ErrorPage errorPage = new ErrorPage();
        errorPage.setExceptionType(exceptionType);
        errorPage.setLocation(location);
        errorPage.setCharset(StandardCharsets.UTF_8);
        return errorPage;
    }
}
