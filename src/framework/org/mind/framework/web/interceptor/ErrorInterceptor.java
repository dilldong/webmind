package org.mind.framework.web.interceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2024/4/18
 */
public interface ErrorInterceptor {
    boolean handleFailure(HttpServletRequest request, HttpServletResponse response, Throwable ex);
}
