package org.mind.framework.dispatcher;

import org.mind.framework.ContextSupport;
import org.mind.framework.container.ContainerAware;
import org.mind.framework.dispatcher.handler.HandlerDispatcherRequest;
import org.mind.framework.dispatcher.handler.HandlerRequest;
import org.mind.framework.dispatcher.support.WebContainerGenerator;
import org.mind.framework.exception.BaseException;
import org.mind.framework.renderer.template.TemplateFactory;
import org.mind.framework.service.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * DispatcherServlet must be mapped to root URL "/". It handles ALL requests
 * from clients, and dispatches to appropriate handler to handle each request.
 *
 * @author dp
 */
public class DispatcherServlet extends HttpServlet {

    private static final long serialVersionUID = 4011062653282779809L;

    private static final Logger log = LoggerFactory.getLogger(DispatcherServlet.class);

    /**
     * HTTP method "GET"
     */
    private static final String METHOD_GET = "GET";

    /**
     * HTTP method "POST"
     */
    private static final String METHOD_POST = "POST";

    /**
     * Request http processer
     */
    private HandlerRequest dispatcher;

    /**
     * Guice/Spring web container
     */
    private ContainerAware webContainer;

    /**
     * Create default HttpServlet
     */
    public DispatcherServlet() {
        super();
    }

    @Override
    public void init() throws ServletException {
        log.info("Initializing mindframework servlet....");

        this.webContainer = WebContainerGenerator.initMindContainer(this.getServletConfig());
        this.webContainer.init(this.getServletConfig());
        List<Object> beans = this.webContainer.loadBeans();

        this.dispatcher = new HandlerDispatcherRequest(this.getServletConfig());
        this.dispatcher.init(beans);

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
        String method = request.getMethod();
        if (!METHOD_GET.equals(method) && !METHOD_POST.equals(method)) {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Unsupported http method.");
            return;
        }

        this.process(request, response);
    }


    @Override
    public void destroy() {
        log.info("Destroy mindframework web container....");
        this.webContainer.destroy();
        this.dispatcher.destroy();
        this.dispatcher = null;
        this.webContainer = null;
    }

    /**
     * Process requests.
     *
     * @param request
     * @param response
     * @throws IOException
     * @throws ServletException
     * @throws BaseException
     */
    private void process(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        this.dispatcher.processor(request, response);
    }


    /**
     * starting web thread service.
     */
    private void startServer() {
        Service serv = (Service) ContextSupport.getBean("mainService", Service.class);
        serv.start();
    }
}
