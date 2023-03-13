package org.mind.framework.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.mind.framework.renderer.Render;
import org.mind.framework.util.HttpUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
public abstract class AbstractHandlerInterceptor implements HandlerInterceptor {

    @Override
    public boolean doBefore(HttpServletRequest request, HttpServletResponse response) {
        return true;
    }

    @Override
    public void doAfter(HttpServletRequest request, HttpServletResponse response) {
    }

    @Override
    public void renderCompletion(HttpServletRequest request, HttpServletResponse response) {
        HttpUtils.clearSetting(request);
    }

    protected boolean render(Render render, HttpServletRequest request, HttpServletResponse response) {
        return render(render, HttpServletResponse.SC_OK, request, response);
    }

    /**
     * Set the HTTP response status code.
     * The default is OK.
     * If you need to return the actual error code,
     * you can it by setting response Status If Error.
     */
    protected boolean render(Render render, int responseStatusIfError, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(responseStatusIfError);

        try {
            render.render(request, response);
        } catch (IOException | ServletException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }
}
