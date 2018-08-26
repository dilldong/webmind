package org.mind.framework.renderer.template;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * Template interface.
 * 
 * @author dp
 */
public interface Template {

    /**
     * Render by template engine.
     * 
     * @param request HttpServletRequest object.
     * @param response HttpServletResponse object.
     * @param model Model as java.util.Map.
     * @throws Exception If render failed.
     */
    void render(HttpServletRequest request, HttpServletResponse response, Map<String, Object> model) throws IOException, ServletException;
}
