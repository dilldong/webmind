package org.mind.framework.renderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Render object to indicate how to rendering the http response.
 *
 * @author dp
 */
public abstract class Render {
    protected static final Logger log = LoggerFactory.getLogger(Render.class);

    protected String contentType;

    public static final String MIME_JAVASCRIPT = "application/x-javascript";

    public static final String FORBIDDEN_HTML = "<div style='text-align:center;margin-top:5vh'><h2>403</h2><h2>Forbidden</h2></div>";

    public static final String BAD_REQUEST_HTML = "<div style='text-align:center;margin-top:5vh'><h2>400</h2><h2>Bad Request</h2></div>";

    public static final String NOT_FOUND_HTML = "<div style='text-align:center;margin-top:5vh'><h2>404</h2><h2>Not Found</h2></div>";

    public static final String SERVER_ERROR_HTML = "<div style='text-align:center;margin-top:5vh'><h2>500</h2><h2>Internal Server Error</h2></div>";

    /**
     * get response content type.
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * set response content type: "text/xml". The default content
     * type is "text/html". Do not add "charset=xxx".
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * render the output of http response.
     *
     * @param request  HttpServletRequest.
     * @param response HttpServletResponse.
     * @throws IOException, ServletException.
     */
    public abstract void render(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;

}
