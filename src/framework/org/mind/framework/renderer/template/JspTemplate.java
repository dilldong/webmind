package org.mind.framework.renderer.template;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
