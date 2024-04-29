package org.mind.framework.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.util.HttpUtils;
import org.mind.framework.util.JsonUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Objects;

/**
 * Holds all Servlet objects in ThreadLocal.
 *
 * @author dp
 */
public final class Action {
    private static final ThreadLocal<Action> ACTION_THREAD_LOCAL = new ThreadLocal<>();

    private final ServletContext context;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    private Action(HttpServletRequest request, HttpServletResponse response) {
        this.context = request.getServletContext();
        this.request = request;
        this.response = response;
    }

    public String getRemoteIp(boolean... forAttr) {
        return HttpUtils.getRequestIP(request, forAttr);
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

    public String urlEncode(String value) {
        if(StringUtils.isEmpty(value))
            return value;

        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            ThrowProvider.doThrow(e);
        }
        return StringUtils.EMPTY;
    }

    public String urlDecode(String value) {
        if(StringUtils.isEmpty(value))
            return value;

        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            ThrowProvider.doThrow(e);
        }
        return StringUtils.EMPTY;
    }

    public MultipartFile getFirstFile(String... keys) {
        MultiValueMap<String, MultipartFile> filesMap = getMultiValueMap();
        if (Objects.isNull(filesMap) || filesMap.isEmpty())
            return null;

        if (ArrayUtils.isEmpty(keys)) {
            String key = filesMap.containsKey("file[0]") ? "file[0]" : "file";
            return filesMap.getFirst(key);
        }

        return filesMap.getFirst(keys[0]);
    }

    public MultiValueMap<String, MultipartFile> getMultiValueMap() {
        if (isMultipartRequest())
            return ((MultipartHttpServletRequest) getRequest()).getMultiFileMap();

        return null;
    }

    public void setAttribute(String name, Object value) {
        getRequest().setAttribute(name, value);
    }

    public <T> T getAttribute(String name) {
        return getAttribute(name, null);
    }

    public <T> T getAttribute(String name, T defaultValue) {
        Object value = getRequest().getAttribute(name);
        if (Objects.isNull(value))
            return defaultValue;

        return (T) value;
    }

    public void removeAttribute(String name) {
        getRequest().removeAttribute(name);
    }

    /**
     * check current request
     */
    public boolean isMultipartRequest() {
        return HttpUtils.isMultipartRequest(getRequest());
    }

    /**
     * Whether the request is POST method?
     */
    public boolean isPostMehod() {
        return HttpUtils.isPostMehod(getRequest());
    }

    /**
     * Whether the request is GET method?
     */
    public boolean isGetMehod() {
        return HttpUtils.isGetMehod(getRequest());
    }

    /**
     * Whether the request is PUT method?
     */
    public boolean isPutMehod() {
        return HttpUtils.isPutMehod(getRequest());
    }

    /**
     * Whether the request is DELETE method?
     */
    public boolean isDeleteMehod() {
        return HttpUtils.isDeleteMehod(getRequest());
    }

    /**
     * Get the byte[] content of the post request
     *
     * @throws IOException
     */
    public byte[] getPostBytes() throws IOException {
        return HttpUtils.getPostBytes(request);
    }

    /**
     * Get the content of the post request
     *
     * @throws IOException
     */
    public String getPostString() throws IOException {
        return HttpUtils.getPostString(request, true);
    }

    public String getJson() {
        return HttpUtils.getJson(request);
    }

    public JsonObject getJsonObject() {
        return JsonUtils.fromJson(getJson(), new TypeToken<JsonObject>() {});
    }

    public JsonArray getJsonArray() {
        return JsonUtils.fromJson(getJson(), new TypeToken<JsonArray>() {});
    }

    public String getString(String name) {
        return request.getParameter(name);
    }

    public String getString(String name, String defaultValue) {
        return StringUtils.defaultIfEmpty(getString(name), defaultValue);
    }

    public long getLong(String name) {
        return Long.parseLong(getString(name));
    }

    public long getLong(String name, long defaultValue) {
        String value = getString(name);
        if (StringUtils.isEmpty(value))
            return defaultValue;

        return Long.parseLong(value);
    }

    public int getInt(String name) {
        return Integer.parseInt(getString(name));
    }

    public int getInt(String name, int defaultValue) {
        String value = getString(name);
        if (StringUtils.isEmpty(value))
            return defaultValue;

        return Integer.parseInt(value);
    }

    public boolean getBoolean(String name) {
        return BooleanUtils.toBoolean(getString(name));
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        String value = getString(name);
        if (StringUtils.isEmpty(value))
            return defaultValue;

        return BooleanUtils.toBoolean(value);
    }

    public String getHeader(String name) {
        return request.getHeader(name);
    }

    public String getHeader(String name, String defaultValue) {
        return StringUtils.defaultIfEmpty(getHeader(name), defaultValue);
    }

    public int getIntHeader(String name) {
        return request.getIntHeader(name);
    }

    public int getIntHeader(String name, int defaultValue) {
        int value = request.getIntHeader(name);
        return value == -1 ? defaultValue : value;
    }

    public long getDateHeader(String name) {
        return request.getDateHeader(name);
    }

    public Enumeration<String> getHeaderNames() {
        return request.getHeaderNames();
    }

    public Enumeration<String> getHeaders(String name) {
        return request.getHeaders(name);
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
        return ACTION_THREAD_LOCAL.get();
    }

    public static void setActionContext(HttpServletRequest request, HttpServletResponse response) {
        ACTION_THREAD_LOCAL.set(new Action(request, response));
    }

    public static void removeActionContext() {
        ACTION_THREAD_LOCAL.remove();
    }
}
