package org.mind.framework.interceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Intercept the execution of a handler,Called after HandlerMapping determined
 *
 * @author dp
 */
public interface HandlerInterceptor {

    /**
     * 在业务处理器处理请求之前被调用
     *
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    boolean doBefore(HttpServletRequest request, HttpServletResponse response);

    /**
     * 在业务处理器处理完成之后，生成视图之前执行
     *
     * @param request
     * @param response
     * @throws Exception
     */
    void doAfter(HttpServletRequest request, HttpServletResponse response);

    /**
     * 在DispatcherServlet完全处理完请求之后被调用，可用于清理资源
     *
     * @param request
     * @param response
     * @throws Exception
     */
    void renderCompletion(HttpServletRequest request, HttpServletResponse response);
}
