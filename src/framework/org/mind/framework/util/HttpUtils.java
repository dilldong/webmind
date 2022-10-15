package org.mind.framework.util;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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

    /**
     * Identify and return the path component (from the request URI) that
     * we will use to select an Action to dispatch with.  If no such
     * path can be identified, create an error response and return
     * <code>null</code>.
     *
     * @param request The servlet request we are processing
     */
    public static String getURI(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();

//		log.info("path info: "+ request.getPathInfo());
//		log.info("servlet path: "+ request.getServletPath());
//		log.info("Query string: "+ request.getQueryString());

        uri = contextPath.length() > 0 ? uri.substring(contextPath.length()) : uri;

        // check is root path
        if (uri.length() > 1) uri = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;

        // uri has sessionid
        int hasJsession = uri.indexOf(";jsessionid");
        if (hasJsession != -1) uri = uri.substring(0, hasJsession);

        return uri;
    }

    public static String getURL(HttpServletRequest request) {
        String scheme = request.getScheme();
        int port = request.getServerPort();
        String urlPath = request.getRequestURI();

        StringBuilder url = new StringBuilder();
        url.append(scheme);
        url.append("://");
        url.append(request.getServerName());

        if (scheme.equals("http") && port != 80 || scheme.equals("https") && port != 443) {
            url.append(':');
            url.append(request.getServerPort());
        }

        url.append(urlPath);
        return url.toString();
    }

    public static String getRequestIP(HttpServletRequest request) {
        String ip = request.getHeader(PROXY_REMOTE_IP_ADDRESS[0]);
        if (StringUtils.isNotEmpty(ip))
            return getRemoteIpFromForward(ip);

        ip = request.getHeader(PROXY_REMOTE_IP_ADDRESS[1]);
        if (StringUtils.isNotEmpty(ip))
            return ip;

        return request.getRemoteAddr();
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
        String json = getPostString(request);
        if (JsonUtils.isJson(json))
            return json;

        return null;
    }

    public static Map<String, JsonObject> getJsonMap(HttpServletRequest request) {
        return JsonUtils.fromJson(
                getJson(request),
                new TypeToken<Map<String, JsonObject>>(){});
    }

    public static List<JsonObject> getJsonList(HttpServletRequest request) {
        return JsonUtils.fromJson(
                getJson(request),
                new TypeToken<List<JsonObject>>(){});
    }

    /**
     * Get the content of the post request
     *
     * @return
     * @throws UnsupportedEncodingException
     */
    public static String getPostString(HttpServletRequest request) {
        byte[] data = null;
        try {
            data = getPostBytes(request);
        } catch (IOException e) {
        }

        /*
         * servlet规范:
         * 一个InputStream对象在被读取完成后，将无法被再次读取，始终返回-1；
         * InputStream并没有实现reset方法（可以重置首次读取的位置），无法实现重置操作；
         */
        if (Objects.nonNull(data)) {
            String encoding = StringUtils.defaultIfEmpty(request.getCharacterEncoding(), StandardCharsets.UTF_8.name());
            try {
                String body = new String(data, encoding);
                request.setAttribute(BODY_PARAMS, body);
                return body;
            } catch (UnsupportedEncodingException e) {
                return null;
            }
        }

        return (String) request.getAttribute(BODY_PARAMS);
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
}
