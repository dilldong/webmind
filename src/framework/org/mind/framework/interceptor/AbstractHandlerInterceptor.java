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
        try {
            render.render(request, response);
        } catch (IOException | ServletException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }
}
