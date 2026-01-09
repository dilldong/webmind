package org.mind.framework.web.dispatcher.handler;

import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.exception.BaseException;
import org.mind.framework.util.HttpUtils;
import org.mind.framework.util.JsonUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Objects;

/**
 * @author dp
 */
public interface HandlerResult {
    String NO_CACHE = "no-cache";
    String REQUEST_URL = "URL";
    String REQUEST_METHOD = "Method";
    String REQUEST_IP = "Request IP";
    String CHECK_MULTIPART = "check_multipart_request";

    String JSON_METHOD = "Json Method";
    String REQUEST_RAW_CONTENT = "Raw Content";

    String JSON_RPC_ID = "id";
    String JSON_RPC_TAG = "jsonrpc";
    String JSON_RPC_METHOD = "method";

    void handleResult(Object result, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;

    static void setRequestAttribute(HttpServletRequest request) {
        JsonObject jsonObject = new JsonObject();
        String queryString = request.getQueryString();

        if (StringUtils.isEmpty(queryString))
            jsonObject.addProperty(REQUEST_URL, HttpUtils.getURL(request, true));
        else {
            try {
                queryString = URLDecoder.decode(queryString, StandardCharsets.UTF_8.name());
            } catch (Exception ignored) {}
            jsonObject.addProperty(REQUEST_URL, HttpUtils.getURL(request, true) + "?" + queryString);
        }

        jsonObject.addProperty(REQUEST_METHOD, request.getMethod());
        jsonObject.addProperty(REQUEST_IP, HttpUtils.getRequestIP(request, true));

        String contentType = request.getContentType();
        if (StringUtils.isNotEmpty(contentType) && contentType.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
            String json = HttpUtils.getJson(request);
            if (StringUtils.isNotEmpty(json)) {
                if (json.contains(JSON_RPC_TAG))
                    jsonObject.addProperty(JSON_METHOD, JsonUtils.getAttribute(JSON_RPC_METHOD, json));
                jsonObject.addProperty(REQUEST_RAW_CONTENT, json);
            }
        } else {
            Enumeration<String> enumeration = request.getParameterNames();
            if (Objects.nonNull(enumeration)) {
                while (enumeration.hasMoreElements()) {
                    String name = enumeration.nextElement();
                    jsonObject.addProperty(name, Arrays.toString(request.getParameterValues(name)));
                }
            }
        }

        // common header
        jsonObject.addProperty(HttpHeaders.CONTENT_TYPE, request.getContentType());
        jsonObject.addProperty(HttpHeaders.REFERER, request.getHeader(HttpHeaders.REFERER));
        jsonObject.addProperty(HttpHeaders.ORIGIN, request.getHeader(HttpHeaders.ORIGIN));

        request.setAttribute(BaseException.EXCEPTION_REQUEST, jsonObject);
    }
}
