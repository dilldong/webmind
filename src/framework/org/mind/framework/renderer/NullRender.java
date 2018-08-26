package org.mind.framework.renderer;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * No need to render, goto directly
 *
 * @author dp
 * @date 2018/08/26
 */
public class NullRender extends Render {

    private String uri;
    private NullRenderType type = NullRenderType.REDIRECT;

    public enum NullRenderType {
        REDIRECT,
        FORWARD;
    }

    public NullRender(String uri) {
        this.uri = uri;
    }

    public NullRender(String uri, NullRenderType type) {
        this.uri = uri;
        this.type = type;
    }

    @Override
    public void render(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        switch (type) {
            case REDIRECT:
                if (uri.startsWith("/"))
                    uri = request.getContextPath() + uri;

                if (log.isInfoEnabled())
                    log.info("Redirect path: " + uri);

                response.sendRedirect(response.encodeRedirectURL(uri));
                break;

            case FORWARD:
                if (log.isInfoEnabled())
                    log.info("Forward path: " + uri);

                // 	Unwrap the multipart request, if there is one.
//	        if (request instanceof MultipartRequestWrapper) {
//	            request = ((MultipartRequestWrapper) request).getRequest();
//	        }

                RequestDispatcher rd = request.getRequestDispatcher(uri);
                if (rd == null) {
                    response.sendError(
                            HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Not forward path: " + uri);
                    return;
                }

                rd.forward(request, response);
                break;
        }
    }
}
