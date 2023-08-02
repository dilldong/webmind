package org.mind.framework.dispatcher.handler;

import org.apache.commons.lang3.StringUtils;
import org.mind.framework.util.DateUtils;
import org.mind.framework.util.ResponseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * @author dp
 */
public class HandlerResourceRequest implements HandlerResult {

    private static final Logger log = LoggerFactory.getLogger("ResourceHandler");

    // forbidden directories
    private static final String[] FORBIDDEN_DIR = new String[]{"/BOOT-INF/", "/WEB-INF/", "/META-INF/"};

    /**
     * Date formats as specified in the HTTP RFC.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7231#section-7.1.1.1">Section 7.1.1.1 of RFC 7231</a>
     */
    private static final String[] DATE_FORMATS = new String[]{
            //Sun, 06 Nov 1994 08:49:37 GMT
            "EEE, dd MMM yyyy HH:mm:ss z",
            // Sunday, 06-Nov-94 08:49:37 GMT
            "EEEEEE, dd-MMM-yy HH:mm:ss zzz",
            //Sun Nov 6 08:49:37 1994
            "EEE MMMM d HH:mm:ss yyyy"
    };

    private final ServletContext servletContext;

    /**
     * response cache header.
     */
    private long expires = 0L;

    /**
     * response cache header.
     */
    private String maxAge = StringUtils.EMPTY;

    public HandlerResourceRequest(ServletConfig config) {
        this.servletContext = config.getServletContext();

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


    /**
     * @param result request URI.
     */
    @Override
    public void handleResult(Object result, HttpServletRequest request,
                             HttpServletResponse response) throws IOException, ServletException {

        String uri = (String) result;
        if(log.isDebugEnabled())
            log.debug("Access resource: {}", uri);
        boolean forbidden = StringUtils.startsWithAny(uri.toUpperCase(), FORBIDDEN_DIR);

        if (forbidden) {
            log.warn("[{}]{} - Forbidden access.", HttpServletResponse.SC_FORBIDDEN, uri);
            HandlerResult.setRequestAttribute(request);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "(403) Forbidden access.");
            return;
        }

        Path path = Paths.get(this.servletContext.getRealPath(uri));
        if (!Files.exists(path)) {
            log.warn("[{}]{} - Access resource is not found.", HttpServletResponse.SC_NOT_FOUND, uri);
            HandlerResult.setRequestAttribute(request);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Access resource is (404) not found.");
            return;
        }

        BasicFileAttributes readAttributes =
                Files.readAttributes(path, BasicFileAttributes.class);

        if (!readAttributes.isRegularFile()) {
            log.warn("[{}]{} - Access resource is not found.", HttpServletResponse.SC_NOT_FOUND, uri);
            HandlerResult.setRequestAttribute(request);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Access resource is (404) not found.");
            return;
        }

        long lastModified = readAttributes.lastModifiedTime().toMillis();
        // Get 'If-Modified-Since' from request header
        long modifiedSince = parseDateHeader(request, HttpHeaders.IF_MODIFIED_SINCE);

        // not modified
        if (modifiedSince != -1 && modifiedSince >= lastModified) {
            if(log.isDebugEnabled())
                log.debug("{} - Resource Not Modified.", HttpServletResponse.SC_NOT_MODIFIED);
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        // Set last modified time in response
        response.setDateHeader(HttpHeaders.LAST_MODIFIED, lastModified);

        long length = readAttributes.size();
        if (length > Integer.MAX_VALUE)
            response.setContentLengthLong(length);
        else
            response.setContentLength((int) length);

        // set cache:
        if (this.expires < 0) {// -1
            response.setHeader(HttpHeaders.CACHE_CONTROL, NO_CACHE);
        } else if (this.expires > 0) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, maxAge);
            // Reset HTTP 1.0 Expires header if present
            response.setDateHeader(HttpHeaders.EXPIRES, DateUtils.getMillis() + this.expires);
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

    private long parseDateHeader(HttpServletRequest request, String headerName) {
        try {
            return request.getDateHeader(headerName);
        } catch (IllegalArgumentException ex) {
            String headerValue = request.getHeader(headerName);
            // Possibly an IE 10 style value: "Wed, 09 Apr 2014 09:57:42 GMT; length=13774"
            if (StringUtils.isNotEmpty(headerValue)) {
                int separatorIndex = headerValue.indexOf(';');
                if (separatorIndex > -1) {
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
            // Default with DateTimeFormatter parsing
            for (String dateFormat : DATE_FORMATS) {
                try {
                    return
                            LocalDateTime
                                    .parse(headerValue, DateTimeFormatter.ofPattern(dateFormat, Locale.US))
                                    .atZone(DateUtils.UTC)
                                    .toEpochSecond() * 1_000L;
                } catch (DateTimeParseException | IllegalArgumentException e){
                    // parse exception, with SimpleDateFormat parsing
                    SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.US);
                    sdf.setTimeZone(DateUtils.UTC_TIMEZONE);
                    try {
                       return sdf.parse(headerValue).getTime();
                    } catch (ParseException ex) {
                        // ignore exception
                    }
                }
            }
        }
        return -1;
    }


}
