package org.mind.framework.util;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.Strings;
import org.mind.framework.exception.BaseException;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.web.server.WebServerConfig;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The common tools for HttpServletRequest
 *
 * @author marcus
 */
public class HttpUtils {
    /**
     * Web 服务器反向代理中用于存放客户端原始 IP 地址的 Http header 名字,
     * 若新增其他的需要增加或者修改其中的值.
     */
    private static final String[] PROXY_REMOTE_IP_ADDRESS = {"X-Forwarded-For", "X-Real-IP"};
    private static final String BODY_PARAMS = "post_body_input_stream";
    private static final String REQUEST_URI = "web_request_uri";
    private static final String REQUEST_URL = "web_request_url";
    private static final String SERVER_URL = "web_request_server";
    private static final String REQUEST_IP = "web_request_ip";

    public static final String GZIP = "gzip";
    public static final String MIME_JAVASCRIPT = "application/x-javascript";

    public static void clearRequestAttribute(HttpServletRequest request) {
        request.removeAttribute(REQUEST_URI);
        request.removeAttribute(REQUEST_URL);
        request.removeAttribute(SERVER_URL);
        request.removeAttribute(REQUEST_IP);
        request.removeAttribute(BODY_PARAMS);
        request.removeAttribute(BaseException.EXCEPTION_REQUEST);
        request.removeAttribute(BaseException.SYS_EXCEPTION);
    }

    /**
     * Identify and return the path component (from the request URI) that
     * we will use to select an Action to dispatch with.  If no such
     * path can be identified, create an error response and return
     * <code>null</code>.
     *
     * @param request The servlet request we are processing
     */
    public static String getURI(HttpServletRequest request, boolean... forAttr) {
        if (ArrayUtils.isEmpty(forAttr) || forAttr[0]) {
            String uri = (String) request.getAttribute(REQUEST_URI);
            if (StringUtils.isNotEmpty(uri))
                return uri;
        }

        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();

        uri = !contextPath.isEmpty() ? uri.substring(contextPath.length()) : uri;

        // check is root path
        if (uri.length() > 1)
            uri = uri.endsWith(org.mind.framework.util.IOUtils.DIR_SEPARATOR) ? uri.substring(0, uri.length() - 1) : uri;

        // uri has sessionid
        int hasJsession = uri.indexOf(";jsessionid");
        if (hasJsession > -1)
            uri = uri.substring(0, hasJsession);

        request.setAttribute(REQUEST_URI, uri);
        return uri;
    }

    public static String getURL(HttpServletRequest request, boolean... forAttr) {
        if (ArrayUtils.isEmpty(forAttr) || forAttr[0]) {
            String url = (String) request.getAttribute(REQUEST_URL);
            if (StringUtils.isNotEmpty(url))
                return url;
        }

        StringBuilder urlBuilder = new StringBuilder(getServerName(request, forAttr));
        String contextPath = WebServerConfig.INSTANCE.getContextPath();
        if (StringUtils.isNotEmpty(contextPath))
            urlBuilder.append(contextPath);

        String url = urlBuilder.append(getURI(request)).toString();
        request.setAttribute(REQUEST_URL, url);
        return url;
    }

    public static String getServerName(HttpServletRequest request, boolean... forAttr) {
        if (ArrayUtils.isEmpty(forAttr) || forAttr[0]) {
            String url = (String) request.getAttribute(SERVER_URL);
            if (StringUtils.isNotEmpty(url))
                return url;
        }

        String scheme = request.getScheme();
        int port = request.getServerPort();

        StringBuilder urlBuilder =
                new StringBuilder()
                        .append(scheme)
                        .append("://")
                        .append(request.getServerName());

        if ("http".equals(scheme) && port != 80 || "https".equals(scheme) && port != 443)
            urlBuilder.append(':').append(request.getServerPort());

        request.setAttribute(SERVER_URL, urlBuilder.toString());
        return urlBuilder.toString();
    }

    public static String getRequestIP(HttpServletRequest request, boolean... forAttr) {
        if (ArrayUtils.isEmpty(forAttr) || forAttr[0]) {
            String ip = (String) request.getAttribute(REQUEST_IP);
            if (StringUtils.isNotEmpty(ip))
                return ip;
        }

        String ip = request.getHeader(PROXY_REMOTE_IP_ADDRESS[0]);
        if (StringUtils.isNotEmpty(ip)) {
            ip = getRemoteIpFromForward(ip);
            request.setAttribute(REQUEST_IP, ip);
            return ip;
        }

        ip = request.getHeader(PROXY_REMOTE_IP_ADDRESS[1]);
        if (StringUtils.isEmpty(ip))
            ip = request.getRemoteAddr();

        request.setAttribute(REQUEST_IP, ip);
        return ip;
    }

    /**
     * 从 HTTP Header 中截取客户端连接 IP 地址。如果经过多次反向代理，
     * 在请求头中获得的是以“,&lt;SP&gt;”分隔 IP 地址链，第一段为客户端 IP 地址。
     *
     * @return 客户端源 IP 地址
     */
    private static String getRemoteIpFromForward(String xforwardIp) {
        //从 HTTP 请求头中获取转发过来的 IP 地址链
        int commaOffset = xforwardIp.indexOf(',');
        if (commaOffset < 0)
            return xforwardIp;

        return xforwardIp.substring(0, commaOffset);
    }

    public static String getJson(HttpServletRequest request) {
        String json = getPostString(request, true);
        if (JsonUtils.isJson(json))
            return json;

        return null;
    }

    /**
     * Get the content of the post request
     *
     * @return
     */
    public static String getPostString(HttpServletRequest request, boolean... forAttr) {
        if (ArrayUtils.isEmpty(forAttr) || forAttr[0]) {
            String json = (String) request.getAttribute(BODY_PARAMS);
            if (StringUtils.isNotEmpty(json))
                return json;
        }

        byte[] data = null;
        try {
            data = getPostBytes(request);
        } catch (IOException ignored) {
        }

        /*
         * Servlet Specification:
         * After an InputStream object is read, it cannot be read again, and always returns -1,
         * InputStream does not implement the reset method (which can reset the position of the first read).
         */
        if (Objects.nonNull(data)) {
            String body = Strings.fromUTF8ByteArray(data);
            String contentType = request.getContentType();

            // delete content blank
            if (StringUtils.isNotEmpty(contentType) && contentType.contains(MediaType.APPLICATION_JSON_VALUE))
                body = JsonUtils.deletionBlank(body);

            request.setAttribute(BODY_PARAMS, body);
            return body;
        }

        return null;
    }

    /**
     * Get the byte[] content of the post request
     *
     * @return
     * @throws IOException
     */
    public static byte[] getPostBytes(HttpServletRequest request) throws IOException {
        int contentLength = request.getContentLength();
        if (contentLength <= 0)
            return null;

        byte[] buffer = new byte[contentLength];
        int actualLen = IOUtils.read(request.getInputStream(), buffer, 0, contentLength);
        if (actualLen != contentLength)
            return null;

        return buffer;
    }

    /**
     * check current request
     */
    public static boolean isMultipartRequest(HttpServletRequest request) {
        return isPostMethod(request) && org.apache.commons.lang3.Strings.CI.startsWith(request.getContentType(), "multipart/");
    }

    public static String encodeURIComponent(String value) {
        if (StringUtils.isEmpty(value))
            return value;

        // 在JS中，encodeURIComponent会保留一些特定字符
        try {
            String encode = URLEncoder.encode(value, StandardCharsets.UTF_8.name());
            return StringUtils.replaceEach(encode,
                    new String[]{"+", "%27", "%7E", "%28", "%29", "%21"},
                    new String[]{"%20", "'", "~", "(", ")", "!"});
        } catch (UnsupportedEncodingException e) {
            ThrowProvider.doThrow(e);
        }
        return StringUtils.EMPTY;
    }

    public static String encodeURI(String value) {
        if (StringUtils.isEmpty(value))
            return value;

        // 在JS中，encodeURI会保留一些特定字符
        String encode = org.apache.catalina.util.URLEncoder.DEFAULT.encode(value, StandardCharsets.UTF_8);
        return StringUtils.replaceEach(encode,
                new String[]{"%23", "%3F"},
                new String[]{"#", "?"});
    }

    public static String decodeURI(String value) {
        if (StringUtils.isEmpty(value))
            return value;

        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * Whether the request is POST method?
     */
    public static boolean isPostMethod(HttpServletRequest request) {
        return RequestMethod.POST.name().equalsIgnoreCase(request.getMethod());
    }

    /**
     * Whether the request is GET method?
     */
    public static boolean isGetMethod(HttpServletRequest request) {
        return RequestMethod.GET.name().equalsIgnoreCase(request.getMethod());
    }

    /**
     * Whether the request is PUT method?
     */
    public static boolean isPutMethod(HttpServletRequest request) {
        return RequestMethod.PUT.name().equalsIgnoreCase(request.getMethod());
    }

    /**
     * Whether the request is DELETE method?
     */
    public static boolean isDeleteMethod(HttpServletRequest request) {
        return RequestMethod.DELETE.name().equalsIgnoreCase(request.getMethod());
    }


}
