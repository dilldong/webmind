package org.mind.framework.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @version 1.0
 * @author Marcus
 * @date 2024/4/18
 */
public interface ErrorInterceptor {
    boolean handleFailure(HttpServletRequest request, HttpServletResponse response, Throwable ex);
}
