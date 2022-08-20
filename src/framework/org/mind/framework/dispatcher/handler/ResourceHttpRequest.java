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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author dp
 */
public class ResourceHttpRequest implements HandlerResult {

    private static final Logger log = LoggerFactory.getLogger(ResourceHttpRequest.class);

    private static final List<String> SAFE_METHODS = Arrays.asList("GET", "HEAD");

    /**
     * Date formats as specified in the HTTP RFC.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7231#section-7.1.1.1">Section 7.1.1.1 of RFC 7231</a>
     */
    private static final String[] DATE_FORMATS = new String[]{
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM dd HH:mm:ss yyyy"
    };

    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    private final ServletContext servletContext;

    /**
     * response cache header.
     */
    private long expires = 0L;

    /**
     * response cache header.
     */
    private String maxAge = StringUtils.EMPTY;

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
        log.debug("Access resource: {}", uri);
        boolean startFlag = StringUtils.startsWithAny(uri.toUpperCase(), new String[]{"/BOOT-INF/", "/WEB-INF/", "/META-INF/"});

        if (startFlag) {
            log.warn("{} - Forbidden access.", HttpServletResponse.SC_FORBIDDEN);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden access.");
            return;
        }

        File file = new File(this.servletContext.getRealPath(uri));

        if (file == null || !file.exists() || !file.isFile()) {
            log.warn("{} - {} - Access resource is not found.", HttpServletResponse.SC_NOT_FOUND, request.getRequestURI());
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Access resource is not found.");
            return;
        }

        long lastModified = file.lastModified();
        // Get 'If-Modified-Since' from request header
        long modifiedSince = parseDateHeader(request, IF_MODIFIED_SINCE);

        // not modified
        if (modifiedSince != -1 && modifiedSince >= (lastModified / 1_000L * 1_000L)) {
            log.debug("{} - Resource Not Modified.", HttpServletResponse.SC_NOT_MODIFIED);
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        // Set last modified time in response
        response.setDateHeader(LAST_MODIFIED, lastModified);

        long length = file.length();
        if (length > Integer.MAX_VALUE)
            response.setContentLengthLong(length);
        else
            response.setContentLength((int) length);

        // set cache:
        if (this.expires < 0) {// -1
            response.setHeader("Cache-Control", "no-cache");
        } else if (this.expires > 0) {
            response.setHeader("Cache-Control", maxAge);
            // Reset HTTP 1.0 Expires header if present
            response.setDateHeader("Expires", DateFormatUtils.getTimeMillis() + this.expires);
        }

        // should download?
//      String name = request.getParameter("_download");
//      if (name!=null) {
//          resp.setContentType(MIME_OCTET_STREAM);
//          resp.setHeader(String.format("Content-disposition", "attachment; filename=%s", name));
//      }

        String mime = servletContext.getMimeType(file.getName());
        response.setContentType(StringUtils.isEmpty(mime) ? "application/octet-stream" : mime);

        // write stream
        ResponseUtils.write(response.getOutputStream(), file);
    }

    private long parseDateHeader(HttpServletRequest request, String headerName) {
        try {
            return request.getDateHeader(headerName);
        } catch (IllegalArgumentException ex) {
            String headerValue = request.getHeader(headerName);
            // Possibly an IE 10 style value: "Wed, 09 Apr 2014 09:57:42 GMT; length=13774"
            if (StringUtils.isNotEmpty(headerValue)) {
                int separatorIndex = headerValue.indexOf(';');
                if (separatorIndex != -1) {
                    String datePart = headerValue.substring(0, separatorIndex);
                    return parseDateValue(datePart);
                }
            }
        }
        return -1;
    }

    private long parseDateValue(String headerValue) {
        if (StringUtils.isEmpty(headerValue))
            return -1;// No header value sent at all

        if (headerValue.length() >= 3) {
            // Short "0" or "-1" like values are never valid HTTP date headers...
            // Let's only bother with SimpleDateFormat parsing for long enough values.
            for (String dateFormat : DATE_FORMATS) {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat, Locale.US);
                simpleDateFormat.setTimeZone(GMT);
                try {
                    return simpleDateFormat.parse(headerValue).getTime();
                } catch (ParseException ex) {
                    simpleDateFormat = null;
                    // ignore exception
                }
            }
        }
        return -1;
    }


}
