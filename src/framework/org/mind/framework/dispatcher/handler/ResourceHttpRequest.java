package org.mind.framework.dispatcher.handler;

import org.apache.commons.lang3.StringUtils;
import org.mind.framework.util.DateFormatUtils;
import org.mind.framework.util.ResponseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 * @author dp
 */
public class ResourceHttpRequest implements HandlerResult {

    private static final Logger log = LoggerFactory.getLogger(ResourceHttpRequest.class);

    private final ServletContext servletContext;

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
            this.maxAge = String.format("max-age=%d", t);
            log.info("Static file's cache time is set to {} seconds.", t);
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
        boolean startFlag = StringUtils.startsWithAny(uri.toUpperCase(), new String[]{"/BOOT-INF/", "/WEB-INF/", "/META-INF/"});

        if (startFlag) {
            log.error("{} - Not Author access.", HttpServletResponse.SC_NON_AUTHORITATIVE_INFORMATION);
            response.sendError(HttpServletResponse.SC_NON_AUTHORITATIVE_INFORMATION, "Not Author access.");
            return;
        }

        File file = new File(this.servletContext.getRealPath(uri));

        if (file == null || !file.isFile()) {
            log.error("{} - {} - Access resource is not found.", HttpServletResponse.SC_NOT_FOUND, request.getRequestURI());
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Access resource is not found.");
            return;
        }

        // set request data header
        long lastModified = file.lastModified();
        long modifiedSince = request.getDateHeader(HEADER_IFMODSINCE);

        // cache
        if (modifiedSince != -1 && modifiedSince >= lastModified) {
            log.info("{} - Resource Not Modified.", HttpServletResponse.SC_NOT_MODIFIED);
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
//          resp.setHeader(String.format("Content-disposition", "attachment; filename=%s", name));
//      }

        String mime = servletContext.getMimeType(file.getName());
        response.setContentType(mime == null ? "application/octet-stream" : mime);

        // write stream
        ResponseUtils.write(response.getOutputStream(), file);
    }

}
