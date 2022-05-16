package org.mind.framework.server;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.util.ServerInfo;
import org.apache.commons.lang.StringUtils;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.mind.framework.dispatcher.DispatcherServlet;
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
@Slf4j
public class TomcatServer extends Tomcat {

    private WebServerConfig serverConfig;

    public TomcatServer(WebServerConfig serverConfig) {
        super();
        this.serverConfig = serverConfig;

        Connector connector = getNioConnector();
        this.getService().addConnector(connector);// getService auto create
        this.setConnector(connector);

        Host host = this.getHost();
        host.setAutoDeploy(false);

        // use JNDI sevice
        this.enableNaming();
    }

    @Override
    public Context addWebapp(Host host, String contextPath, String docBase) {
        StandardContext ctx = new StandardContext();
        ctx.setPath(contextPath);
        ctx.setDocBase(docBase);

        // by web.xml
        if (StringUtils.isNotEmpty(serverConfig.getWebXml())) {
            log.debug("Load Web-Server config: {}", serverConfig.getWebXml());
            ctx.addLifecycleListener(this.getDefaultWebXmlListener());
            LifecycleListener config = this.getContextListener(host);
            ctx.addLifecycleListener(config);

            if (config instanceof ContextConfig)
                ((ContextConfig) config).setDefaultWebXml(new File(serverConfig.getWebXml()).getAbsolutePath());
        } else {
            log.debug("Creation mindframework servlet: [{}]", ServerContext.SERVLET_CLASS);
            this.createServlet(host, ctx);// create servlet

            // Add error | exception page
            ctx.addErrorPage(newErrorPage(400, "/error/400"));
            ctx.addErrorPage(newErrorPage(404, "/error/404"));
            ctx.addErrorPage(newErrorPage(500, "/error/500"));
            ctx.addErrorPage(newErrorPage("java.lang.NullPointerException", "/error/199"));
            ctx.addErrorPage(newErrorPage("javax.servlet.ServletException", "/error/199"));
            ctx.addErrorPage(newErrorPage("java.lang.Exception", "/error/199"));
        }

        host.addChild(ctx);
        return ctx;
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

    public void logServer() {
        ServerInfo.main(null);
        System.out.println("CATALINA_BASE:  " + System.getProperty("catalina.base"));
        System.out.println("CATALINA_HOME:  " + System.getProperty("catalina.home"));
        ManagementFactory.getRuntimeMXBean().getInputArguments().forEach(arg -> System.out.println("Command line argument: " + arg));
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
            XmlWebApplicationContext xmas = new XmlLoad4SpringContext();
            xmas.setConfigLocations(serverConfig.getSpringFileSet().toArray(new String[serverConfig.getSpringFileSet().size()]));// load spring config
            loadResource(xmas.getEnvironment());// load properties
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
            }
        });

        propertySources.forEach(res -> {
            if (res.getName().startsWith("class path resource"))
                log.info("Loading resource: [{}]", StringUtils.substringBetween(res.getName(), "[", "]"));
        });
    }

    private LifecycleListener getContextListener(Host host) {
        try {
            Class<?> clazz = Class.forName(host.getConfigClass());
            return (LifecycleListener) clazz.getConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException(ex);
        }
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
