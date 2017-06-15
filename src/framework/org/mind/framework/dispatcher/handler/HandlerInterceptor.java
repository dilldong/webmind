package org.mind.framework.dispatcher.handler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Intercept the execution of a handler,Called after HandlerMapping determined
 *
 * @author dp
 */
public interface HandlerInterceptor {

    boolean doBefore(Object handler, HttpServletRequest request, HttpServletResponse response) throws Exception;

    boolean doAfter(Object handler, Object handlerResult, HttpServletRequest request, HttpServletResponse response) throws Exception;

    void renderCompletion(Object handler, HttpServletRequest request, HttpServletResponse response) throws Exception;
}
