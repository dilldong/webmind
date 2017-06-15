package org.mind.framework.dispatcher.handler;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mind.framework.util.DateFormatUtils;
import org.mind.framework.util.ResponseUtils;

/**
 * @author dp
 */
public class ResourceHttpRequest implements HandlerResult {

    private static final Log log = LogFactory.getLog(ResourceHttpRequest.class);

    private ServletContext servletContext;

    /**
     * response cache header.
     */
    private long expires = 0L;

    /**
     * response cache header.
     */
    private String maxAge = "";

    public ResourceHttpRequest(ServletConfig config) {
        this.servletContext = config.getServletContext();

        String expSec = config.getInitParameter("expires");
        if (expSec == null) {
            this.expires = 0L;
            return;
        }

        int t = Integer.parseInt(expSec);
        if (t > 0) {
            this.expires = t * 1000L;// ms
            this.maxAge = "max-age=" + t;
            log.info("Static file's cache time is set to " + t + " seconds.");
        } else if (t < 0) {
            this.expires = -1L;
            log.info("Static file is set to no cache.");
        }
    }

    /**
     * @param result request URI.
     */
    @Override
    public void handleResult(Object result, HttpServletRequest request,
                             HttpServletResponse response) throws IOException, ServletException {

        String uri = (String) result;
        if (uri.toUpperCase().startsWith("/WEB-INF/") || uri.toUpperCase().startsWith("/META-INF/")) {
            log.error(Response.SC_NON_AUTHORITATIVE_INFORMATION + " - Not Author access.");
            response.sendError(Response.SC_NON_AUTHORITATIVE_INFORMATION, "Not Author access.");
            return;
        }

        File file = new File(this.servletContext.getRealPath(uri));

        if (file == null || !file.isFile()) {
            log.error(Response.SC_NOT_FOUND + " - " + request.getRequestURI() + " - Access resource is not found.");
            response.sendError(Response.SC_NOT_FOUND, "Access resource is not found.");
            return;
        }

        // set request data header
        long lastModified = file.lastModified();
        long modifiedSince = request.getDateHeader(HEADER_IFMODSINCE);

        // cache
        if (modifiedSince != -1 && modifiedSince >= lastModified) {
            log.info(HttpServletResponse.SC_NOT_MODIFIED + " - Resource Not Modified.");
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        // no cache.
        response.setDateHeader(HEADER_LASTMOD, lastModified);
        response.setContentLength((int) file.length());

        // set cache:
        if (this.expires < 0) {// -1
            response.setHeader("Cache-Control", "no-cache");
        } else if (this.expires > 0) {
            response.setHeader("Cache-Control", maxAge);
            response.setDateHeader("Expires", DateFormatUtils.getTimeMillis() + this.expires);
        }

        // should download?
//      String name = request.getParameter("_download");
//      if (name!=null) {
//          resp.setContentType(MIME_OCTET_STREAM);
//          resp.setHeader("Content-disposition", "attachment; filename=" + name);
//      }

        String mime = servletContext.getMimeType(file.getName());
        response.setContentType(mime == null ? "application/octet-stream" : mime);

        // write stream
        ResponseUtils.write(response.getOutputStream(), file);
    }


//	http://www.jarvana.com/jarvana/view/org/springframework/spring-webmvc/3.1.0.RELEASE/spring-webmvc-3.1.0.RELEASE-sources.jar!/org/springframework/web/servlet/support/WebContentGenerator.java?format=ok


}
