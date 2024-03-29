package org.mind.framework.web.renderer;

import org.mind.framework.util.IOUtils;
import org.mind.framework.web.dispatcher.handler.HandlerResult;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

/**
 * No need to render, goto directly
 *
 * @author dp
 * @date 2018/08/26
 */
public class NullRender extends Render {

    private String uri;
    private RenderType type = RenderType.FORWARD;

    public NullRender(String uri) {
        this.uri = uri;
    }

    public NullRender(String uri, RenderType type) {
        this.uri = uri;
        this.type = type;
    }

    @Override
    public void render(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        switch (type) {
            case REDIRECT:
                if (uri.startsWith(IOUtils.DIR_SEPARATOR))
                    uri = String.format("%s%s", request.getContextPath(), uri);

                if (log.isDebugEnabled())
                    log.debug("Redirect path: {}", uri);
                response.sendRedirect(response.encodeRedirectURL(uri));
                break;

            case FORWARD:
                if (log.isDebugEnabled())
                    log.debug("Forward path: {}", uri);

                // 	Unwrap the multipart request, if there is one.
//	        if (request instanceof MultipartRequestWrapper) {
//	            request = ((MultipartRequestWrapper) request).getRequest();
//	        }

                RequestDispatcher rd = request.getRequestDispatcher(uri);
                if (Objects.isNull(rd)) {
                    HandlerResult.setRequestAttribute(request);
                    response.sendError(
                            HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            String.format("Not forward path: %s", uri));
                    return;
                }

                rd.forward(request, response);
                break;
            default:
                break;
        }
    }
}
