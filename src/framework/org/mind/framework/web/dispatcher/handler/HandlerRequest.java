package org.mind.framework.web.dispatcher.handler;

import org.mind.framework.util.HttpUtils;
import org.mind.framework.web.Destroyable;
import org.mind.framework.web.container.ContainerAware;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

    void init(ContainerAware container) throws ServletException;

    void processor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;

    default void clear(HttpServletRequest request){
        HttpUtils.clearSetting(request);
    };

}
