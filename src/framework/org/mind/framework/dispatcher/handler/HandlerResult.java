package org.mind.framework.dispatcher.handler;

import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.exception.BaseException;
import org.mind.framework.util.HttpUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author dp
 */
public interface HandlerResult {
    String NO_CACHE = "no-cache";
    String REQUEST_URL = "URL";
    String REQUEST_METHOD = "Method";
    String REQUEST_IP = "Request IP";

    String JSON_METHOD = "Json Method";
    String REQUEST_RAW_CONTENT = "Raw Content";

    void handleResult(Object result, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;

    static void setRequestAttribute(HttpServletRequest request) {
        JsonObject jsonObject = new JsonObject();
        String queryString = request.getQueryString();

        if (StringUtils.isEmpty(queryString))
            jsonObject.addProperty(REQUEST_URL, HttpUtils.getURL(request, true));
        else
            jsonObject.addProperty(REQUEST_URL, HttpUtils.getURL(request, true) + "?" + queryString);

        jsonObject.addProperty(REQUEST_METHOD, request.getMethod());
        jsonObject.addProperty(REQUEST_IP, HttpUtils.getRequestIP(request, true));

        String str = HttpUtils.getJson(request);
        jsonObject.addProperty(REQUEST_RAW_CONTENT, StringUtils.defaultIfEmpty(str, StringUtils.EMPTY));

        if (StringUtils.isNotEmpty(str) && str.contains("jsonrpc")) {
            str = StringUtils.substringBetween(str, "method", ",");
            str = str.replaceAll("[\'\":]*", StringUtils.EMPTY).trim();
            jsonObject.addProperty(JSON_METHOD, str);
        }
        request.setAttribute(BaseException.EXCEPTION_REQUEST, jsonObject);
    }
}
