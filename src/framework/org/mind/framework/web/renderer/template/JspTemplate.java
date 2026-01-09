package org.mind.framework.web.renderer.template;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * Template using JSP which forward to specific JSP page.
 *
 * @author dp
 */
public class JspTemplate implements Template {

    private final String path;

    public JspTemplate(String path) {
        this.path = path;
    }

    /**
     * Execute the JSP with given model.
     *
     * @throws ServletException
     * @throws IOException
     */
    public void render(
            HttpServletRequest request,
            HttpServletResponse response,
            Map<String, Object> model) throws IOException, ServletException {

        model.forEach(request::setAttribute);
        request.getRequestDispatcher(path).forward(request, response);
    }

}
