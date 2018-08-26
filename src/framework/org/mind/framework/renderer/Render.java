package org.mind.framework.renderer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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

    protected static final Log log = LogFactory.getLog(Render.class);

    protected String contentType;

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
     * @throws any Exception.
     */
    public abstract void render(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;

}
