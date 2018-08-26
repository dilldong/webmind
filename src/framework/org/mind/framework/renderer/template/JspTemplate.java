package org.mind.framework.renderer.template;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Template using JSP which forward to specific JSP page.
 *
 * @author dp
 */
public class JspTemplate implements Template {

    private static final Log log = LogFactory.getLog(JspTemplate.class);

    private String path;

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

        Set<String> keys = model.keySet();
        for (String key : keys) {
            request.setAttribute(key, model.get(key));
        }

        request.getRequestDispatcher(path).forward(request, response);
    }

}
