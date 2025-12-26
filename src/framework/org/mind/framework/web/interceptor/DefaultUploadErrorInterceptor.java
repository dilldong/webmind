package org.mind.framework.web.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.mind.framework.util.ViewResolver;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @version 1.0
 * @author Marcus
 * @date 2024/4/18
 */
@Slf4j
public class DefaultUploadErrorInterceptor extends AbstractHandlerInterceptor implements ErrorInterceptor {

    @Override
    public boolean handleFailure(HttpServletRequest request, HttpServletResponse response, Throwable ex) {
        if (ex instanceof MaxUploadSizeExceededException) {
            return super.renderToFinish(
                    ViewResolver.text(
                            ViewResolver.<String>response(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "File size exceeds limit.").toJson()),
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    request,
                    response);
        }

        log.error(ex.getMessage(), ex);
        return false;
    }
}
