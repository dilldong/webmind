package org.mind.framework;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.mind.framework.annotation.Interceptor;
import org.mind.framework.web.interceptor.AbstractHandlerInterceptor;
import org.springframework.stereotype.Component;

/**
 * @author Marcus
 * @version 1.0
 */
@Slf4j
@Component
@Interceptor(excludes = {"/error/*"})
public class InterceptorTest extends AbstractHandlerInterceptor {

    @Override
    public boolean doBefore(HttpServletRequest request, HttpServletResponse response) {
        if(log.isDebugEnabled())
            log.debug("Interceptor doBefore ....");
        return super.doBefore(request, response);
    }

    @Override
    public void doAfter(HttpServletRequest request, HttpServletResponse response) {
        if(log.isDebugEnabled())
            log.debug("Interceptor doAfter ....");
    }

    @Override
    public void renderCompletion(HttpServletRequest request, HttpServletResponse response) {
        super.renderCompletion(request, response);
        if(log.isDebugEnabled())
            log.debug("Interceptor render complete ....");
    }
}
