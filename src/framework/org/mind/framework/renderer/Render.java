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

    public static final String BAD_REQUEST_HTML = "<html><head><title>400 Bad Request</title></head><body bgcolor='white'><center><h1>400 Bad Request</h1></center><hr><center><a href='https://docs.solarpath.io'>docs.solarpath.io</a></center></body></html>";

    public static final String FORBIDDEN_HTML = "<html><head><title>403 Forbidden</title></head><body bgcolor='white'><center><h1>403 Forbidden</h1></center><hr><center><a href='https://docs.solarpath.io'>docs.solarpath.io</a></center></body></html>";

    public static final String NOT_FOUND_HTML = "<html><head><title>404 Not Found</title></head><body bgcolor='white'><center><h1>404 Not Found</h1></center><hr><center><a href='https://docs.solarpath.io'>docs.solarpath.io</a></center></body></html>";

    public static final String SERVER_ERROR_HTML = "<html><head><title>500 Internal Server Error</title></head><body bgcolor='white'><center><h1>500 Internal Server Error</h1></center><hr><center><a href='https://docs.solarpath.io'>docs.solarpath.io</a></center></body></html>";

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
