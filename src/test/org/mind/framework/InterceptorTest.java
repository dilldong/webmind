package org.mind.framework;

import lombok.extern.slf4j.Slf4j;
import org.mind.framework.annotation.Interceptor;
import org.mind.framework.interceptor.AbstractHandlerInterceptor;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Marcus
 * @version 1.0
 */
@Slf4j
@Component
@Interceptor(excludes = {"/error/*"}, order = 1)
public class InterceptorTest extends AbstractHandlerInterceptor {

    @Override
    public boolean doBefore(HttpServletRequest request, HttpServletResponse response) {
        log.debug("Interceptor doBefore ....");
        return super.doBefore(request, response);
    }

    @Override
    public void doAfter(HttpServletRequest request, HttpServletResponse response) {
        log.debug("Interceptor doAfter ....");
    }

    @Override
    public void renderCompletion(HttpServletRequest request, HttpServletResponse response) {
        super.renderCompletion(request, response);
        log.debug("Interceptor render complete ....");
    }
}
