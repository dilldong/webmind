package org.mind.framework.web.dispatcher.handler;

import org.mind.framework.web.container.ContainerAware;
import org.mind.framework.web.container.Destroyable;

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

    void init(ContainerAware container) throws ServletException;

    void processor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;

}
