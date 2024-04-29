package org.mind.framework.web.dispatcher;

import org.mind.framework.ContextSupport;
import org.mind.framework.exception.BaseException;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.service.Service;
import org.mind.framework.web.container.ContainerAware;
import org.mind.framework.web.dispatcher.handler.DispatcherHandlerRequest;
import org.mind.framework.web.dispatcher.handler.HandlerRequest;
import org.mind.framework.web.dispatcher.handler.HandlerResult;
import org.mind.framework.web.dispatcher.support.WebContainerGenerator;
import org.mind.framework.web.renderer.template.TemplateFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.http.HttpHeaders;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

/**
 * DispatcherServlet must be mapped to root URL "/". It handles ALL requests
 * from clients, and dispatches to appropriate handler to handle each request.
 *
 * @author dp
 */
public class DispatcherServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(DispatcherServlet.class);

    // HandlerRequest object in the bean factory
    private static final String HANDLER_REQUEST_BEAN_NAME = "handlerRequest";

    /**
     * Request http processor
     */
    private HandlerRequest handler;

    /**
     * Guice/Spring web container
     */
    private ContainerAware webContainer;

    @Override
    public void init() throws ServletException {
        log.info("Initializing mind-framework servlet....");

        this.webContainer = WebContainerGenerator.initMindContainer(this.getServletConfig());
        this.webContainer.init(this.getServletConfig());

        this.handler = this.initHandlerRequest();
        this.handler.init(this.webContainer);

        TemplateFactory tf = WebContainerGenerator.initTemplateFactory(this.getServletConfig());
        tf.init(getServletContext());
        TemplateFactory.setTemplateFactory(tf);

        this.startServer();
    }

    /**
     * Receives standard HTTP requests from the public
     * <code>service</code> method and dispatches
     * them to the <code>process</code> methods defined in
     * this class. This method is an HTTP-specific version of the
     * {@link javax.servlet.Servlet#service} method. There's no
     * need to override this method.
     *
     * @param request  the {@link HttpServletRequest} object that
     *                 contains the request the client made of
     *                 the servlet
     * @param response the {@link HttpServletResponse} object that
     *                 contains the response the servlet returns
     *                 to the client
     * @throws IOException      if an input or output error occurs
     *                          while the servlet is handling the
     *                          HTTP request
     * @throws ServletException if the HTTP request
     *                          cannot be handled
     * @see javax.servlet.Servlet#service
     */
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        super.service(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.process(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.process(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.process(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.process(req, resp);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        resp.setHeader(HttpHeaders.ALLOW, "GET, POST, PUT, DELETE, HEAD, OPTIONS");
    }

    @Override
    public void destroy() {
        log.info("Destroy mind-framework web container....");
        this.webContainer.destroy();
        this.handler.destroy();
        this.handler = null;
        this.webContainer = null;
    }

    /**
     * Process requests.
     *
     * @throws IOException
     * @throws ServletException
     */
    private void process(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        try {
            this.handler.processor(request, response);
        } catch (Throwable e) {
            Throwable c = Objects.isNull(e.getCause()) ? e : e.getCause();
            request.setAttribute(BaseException.SYS_EXCEPTION, c);
            HandlerResult.setRequestAttribute(request);
            ThrowProvider.doThrow(c);
        } finally {
            this.handler.clear(request);
        }
    }

    private HandlerRequest initHandlerRequest() {
        try {
            return ContextSupport.getBean(HANDLER_REQUEST_BEAN_NAME, HandlerRequest.class);
        } catch (NoSuchBeanDefinitionException ignored) {}

        return new DispatcherHandlerRequest();
    }

    /**
     * starting web thread service.
     */
    private void startServer() {
        try {
            Service serv = ContextSupport.getBean("mainService", Service.class);
            serv.start();
        } catch (NoSuchBeanDefinitionException e) {
            log.warn("Message Queuing service failed to start, {}.", e.getMessage());
        }
    }
}
