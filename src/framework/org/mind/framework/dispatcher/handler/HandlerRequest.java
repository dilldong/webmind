package org.mind.framework.dispatcher.handler;

import org.mind.framework.container.Destroyable;

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

    void init(List<Object> objects) throws ServletException;

    void processor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;

}
