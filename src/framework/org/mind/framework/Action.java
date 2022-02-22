package org.mind.framework;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.mind.framework.util.JsonUtils;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Holds all Servlet objects in ThreadLocal.
 *
 * @author dp
 */
public final class Action {

    private static final String BODY_PARAMS = "body_input_param";

    private static final ThreadLocal<Action> actionContext =
            new ThreadLocal<Action>();

    private ServletContext context;
    private HttpServletRequest request;
    private HttpServletResponse response;

    /**
     * Web 服务器反向代理中用于存放客户端原始 IP 地址的 Http header 名字，
     * 若新增其他的需要增加或者修改其中的值。
     */
    private static final String[] PROXY_REMOTE_IP_ADDRESS = {"X-Forwarded-For", "X-Real-IP"};

    public String getRemoteIp() {
        String ip = this.request.getHeader(PROXY_REMOTE_IP_ADDRESS[0]);
        if (ip != null && ip.trim().length() > 0)
            return this.getRemoteIpFromForward(ip);

        ip = this.request.getHeader(PROXY_REMOTE_IP_ADDRESS[1]);
        if (ip != null && ip.trim().length() > 0)
            return ip;

        return this.request.getRemoteAddr();
    }

    /**
     * 从 HTTP Header 中截取客户端连接 IP 地址。如果经过多次反向代理，
     * 在请求头中获得的是以“,&lt;SP&gt;”分隔 IP 地址链，第一段为客户端 IP 地址。
     *
     * @return 客户端源 IP 地址
     */
    private String getRemoteIpFromForward(String xforwardIp) {
        //从 HTTP 请求头中获取转发过来的 IP 地址链
        int commaOffset = xforwardIp.indexOf(',');
        if (commaOffset < 0)
            return xforwardIp;

        return xforwardIp.substring(0, commaOffset);
    }

    /**
     * Return the ServletContext of current web application.
     */
    public ServletContext getServletContext() {
        return context;
    }

    /**
     * Return current request object.
     */
    public HttpServletRequest getRequest() {
        return request;
    }

    /**
     * Get the byte[] content of the post request
     *
     * @param request
     * @return
     * @throws IOException
     */
    public byte[] getRequestPostBytes(HttpServletRequest request) throws IOException {
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
     * Get the content of the post request
     *
     * @param request
     * @return
     * @throws IOException
     */
    public String getRequestPostString(HttpServletRequest request) throws IOException {
        byte[] data = getRequestPostBytes(request);
        if (data != null) {
            String encoding = StringUtils.defaultIfEmpty(request.getCharacterEncoding(), StandardCharsets.UTF_8.name());
            String body = new String(data, encoding);
            /*
             * servlet规范:
             * 一个InputStream对象在被读取完成后，将无法被再次读取，始终返回-1；
             * InputStream并没有实现reset方法（可以重置首次读取的位置），无法实现重置操作；
             */
            request.setAttribute(BODY_PARAMS, body);
        }

        return (String) request.getAttribute(BODY_PARAMS);
    }

    public String requestJson(HttpServletRequest request) {
        try {
            return this.getRequestPostString(request);
        } catch (IOException e) {
        }
        return null;
    }

    public Map<String, JsonObject> requestJsonMap(HttpServletRequest request) {
        return JsonUtils.fromJson(
                requestJson(request),
                new TypeToken<Map<String, JsonObject>>() {});
    }

    public List<JsonObject> requestJsonList(HttpServletRequest request) {
        return JsonUtils.fromJson(
                requestJson(request),
                new TypeToken<List<JsonObject>>() {});
    }


    /**
     * Return current response object.
     */
    public HttpServletResponse getResponse() {
        return response;
    }

    /**
     * Return current session object.
     */
    public HttpSession getSession() {
        return request.getSession();
    }

    /**
     * Get current Action object.
     */
    public static Action getActionContext() {
        return actionContext.get();
    }

    public static void setActionContext(ServletContext context, HttpServletRequest request, HttpServletResponse response) {
        Action ctx = new Action();
        ctx.context = context;
        ctx.request = request;
        ctx.response = response;
        actionContext.set(ctx);
    }

    public static void removeActionContext() {
        actionContext.remove();
    }
}
