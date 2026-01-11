package org.mind.framework.web.renderer;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mind.framework.util.IOUtils;
import org.mind.framework.web.dispatcher.handler.HandlerResult;

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
            case REDIRECT -> {
                if (uri.startsWith(IOUtils.DIR_SEPARATOR))
                    uri = String.format("%s%s", request.getContextPath(), uri);

                log.debug("Redirect path: {}", uri);
                response.sendRedirect(response.encodeRedirectURL(uri));
            }
            case FORWARD -> {
                log.debug("Forward path: {}", uri);

                // 	Unwrap the multipart request, if there is one.
//	        if (request instanceof MultipartRequestWrapper multiRequest) {
//	            request = multiRequest.getRequest();
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
            }
        }
    }
}
