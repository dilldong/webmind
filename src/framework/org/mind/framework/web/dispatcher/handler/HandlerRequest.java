package org.mind.framework.web.dispatcher.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mind.framework.util.HttpUtils;
import org.mind.framework.web.Destroyable;
import org.mind.framework.web.container.ContainerAware;

import java.io.IOException;

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

    void init(ContainerAware container) throws ServletException;

    void processor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;

    default void clear(HttpServletRequest request){
        HttpUtils.clearRequestAttribute(request);
    }

}
