package org.mind.framework;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.ThreadContext;
import org.mind.framework.annotation.Interceptor;
import org.mind.framework.util.RandomCodeUtil;
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
    private static final String REQUEST_ID = "requestId";

    @Override
    public boolean doBefore(HttpServletRequest request, HttpServletResponse response) {
        ThreadContext.put(REQUEST_ID, RandomCodeUtil.fastRandomString(6));
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
        ThreadContext.remove(REQUEST_ID);
    }
}
