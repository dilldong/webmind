package org.mind.framework.web.dispatcher.handler;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.mind.framework.util.DateUtils;
import org.mind.framework.util.HttpUtils;
import org.mind.framework.util.IOUtils;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.util.ResponseUtils;
import org.mind.framework.util.ViewResolver;
import org.mind.framework.web.renderer.Render;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.ServletWebRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @author dp
 */
public class ResourceHandlerRequest implements ResourceRequest {

    private static final Logger log = LoggerFactory.getLogger("ResourceHandler");

    // forbidden directories
    private static final String[] FORBIDDEN_DIR = {"/BOOT-INF/", "/WEB-INF/", "/META-INF/"};

    private final ServletContext servletContext;

    // static resource decl.
    private final String resStr;

    /**
     * response cache header.
     */
    private long expires = 0L;

    /**
     * response cache header.
     */
    private String maxAge = StringUtils.EMPTY;

    public ResourceHandlerRequest(ServletConfig config) {
        this.servletContext = config.getServletContext();

        // load web application static resource strs.
        this.resStr = config.getInitParameter("resource");
        log.debug("resource suffix: {}", resStr);

        String expSec = config.getInitParameter("expires");
        if (StringUtils.isEmpty(expSec)) {
            this.expires = 0L;
            return;
        }

        int t = Integer.parseInt(expSec);
        if (t > 0) {
            this.expires = t * 1_000L;// ms
            this.maxAge = String.format("max-age=%d", t);
            log.info("Static file's cache time is set to {} seconds.", t);
        } else if (t < 0) {
            this.expires = -1L;
            log.info("Static file is set to no cache.");
        }
    }

    @Override
    public boolean checkStaticResource(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        final String requestURI = HttpUtils.getURI(request);
        int subIndex = requestURI.lastIndexOf(IOUtils.DOT_SEPARATOR);
        if (subIndex != -1) {
            String suffix = requestURI.substring(subIndex + 1);

            // return true is http request static resource.
            if (MatcherUtils.matcher(
                    suffix,
                    this.resStr,
                    MatcherUtils.IGNORECASE_EQ).matches()) {

                this.handleResult(requestURI, request, response);
                return true;
            }
        }
        return false;
    }


    /**
     * @param result request URI.
     */
    @Override
    public void handleResult(Object result,
                             HttpServletRequest request,
                             HttpServletResponse response) throws IOException, ServletException {
        String uri = (String) result;
        log.debug("Access resource: {}", uri);

        boolean forbidden = Strings.CS.startsWithAny(uri.toUpperCase(), FORBIDDEN_DIR);

        if (forbidden) {
            log.warn("[{}]{} - Forbidden access.", HttpServletResponse.SC_FORBIDDEN, uri);
            this.renderError(HttpServletResponse.SC_FORBIDDEN, Render.FORBIDDEN_HTML, request, response);
            return;
        }

        Path path = Paths.get(this.servletContext.getRealPath(uri));
        if (!Files.exists(path)) {
            log.warn("[{}]{} - Access resource is not found.", HttpServletResponse.SC_NOT_FOUND, uri);
            this.renderError(HttpServletResponse.SC_NOT_FOUND, Render.NOT_FOUND_HTML, request, response);
            return;
        }

        BasicFileAttributes readAttributes =
                Files.readAttributes(path, BasicFileAttributes.class);

        if (!readAttributes.isRegularFile()) {
            log.warn("[{}]{} - Access resource is not found.", HttpServletResponse.SC_NOT_FOUND, uri);
            this.renderError(HttpServletResponse.SC_NOT_FOUND, Render.NOT_FOUND_HTML, request, response);
            return;
        }

        // content size
        long contentSize = readAttributes.size();

        // Process last-modified header, if supported by the handler.
        String method = request.getMethod();
        boolean isGet = HttpMethod.GET.matches(method);
        if (isGet || HttpMethod.HEAD.matches(method)) {
            long lastModified = readAttributes.lastModifiedTime().toMillis();
            String eTag = this.generateETag(contentSize, lastModified / 1_000L * 1_000L);
            if (new ServletWebRequest(request, response).checkNotModified(eTag, lastModified) && isGet)
                return;
        }

        if (contentSize > Integer.MAX_VALUE)
            response.setContentLengthLong(contentSize);
        else
            response.setContentLength((int) contentSize);

        // set cache:
        if (this.expires < 0) {// -1
            response.setHeader(HttpHeaders.CACHE_CONTROL, NO_CACHE);
        } else if (this.expires > 0) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, maxAge);
            // Reset HTTP 1.0 Expires header if present
            response.setDateHeader(HttpHeaders.EXPIRES, DateUtils.CachedTime.currentMillis() + this.expires);
        }

        // should download?
//      String name = request.getParameter("_download");
//      if (name!=null) {
//          resp.setContentType(MIME_OCTET_STREAM);
//          resp.setHeader(String.format("Content-disposition", "attachment; filename=%s", name));
//      }

        String mime = servletContext.getMimeType(path.getFileName().toString());
        response.setContentType(StringUtils.isEmpty(mime) ? MediaType.APPLICATION_OCTET_STREAM_VALUE : mime);

        // write stream
        ResponseUtils.write(response.getOutputStream(), path);
    }

    protected String generateETag(long contentLength, long lastModified) {
        if (contentLength >= 0L || lastModified >= 0L) {
            return new StringBuilder("W/\"")
                    .append(contentLength)
                    .append("-")
                    .append(lastModified)
                    .append("\"")
                    .toString();
        }
        return null;
    }

    protected void renderError(int statusCode, String htmlMessage, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setStatus(statusCode);
        // html body
        ViewResolver.text(htmlMessage).render(request, response);
    }
}
