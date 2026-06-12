package org.mind.framework.web.dispatcher.handler;

import org.mind.framework.web.Destroyable;
import org.mind.framework.web.async.AsyncRequestContext;
import org.mind.framework.web.container.ContainerAware;
import org.mind.framework.web.interceptor.HandlerInterceptor;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Handler dispatch request processor.
 *
 * @author dp
 */
public interface HandlerRequest extends Destroyable {

    // MultipartResolver object in the bean factory
    String MULTIPART_RESOLVER_BEAN_NAME = "multipartResolver";

    // ResourceRequest object in the bean factory
    String RESOURCE_HANDLER_BEAN_NAME = "resourceHandlerRequest";

    String MULTIPART_EXCEPTION = "multipartException";

    String GLOBAL_EXCEPTION = "globalException";

    void init(ContainerAware container) throws ServletException;

    void processor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;

    void execute(String requestUri,
                 Execution execution,
                 HttpServletRequest request,
                 HttpServletResponse response,
                 List<HandlerInterceptor> interceptors) throws ServletException, IOException;

    void execute(String requestUri,
                 Execution execution,
                 AsyncRequestContext asyncContext,
                 List<HandlerInterceptor> interceptors) throws ServletException, IOException;

    void customizeResponse(HttpServletRequest request, HttpServletResponse response);

    void renderError(int statusCode,
                     String jsonMessage,
                     String htmlMessage,
                     HttpServletRequest request,
                     HttpServletResponse response) throws ServletException, IOException;

    boolean doBeforeInterceptors(String requestUri,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 List<HandlerInterceptor> out);

    void clear(HttpServletRequest request);

}