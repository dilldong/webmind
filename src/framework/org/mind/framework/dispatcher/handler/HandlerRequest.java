package org.mind.framework.dispatcher.handler;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mind.framework.container.Destroyable;

/**
 * Handler dispatch request processer.
 *
 * @author dp
 */
public interface HandlerRequest extends Destroyable {

    void init(List<Object> objects);

    void processor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;

}
