package org.mind.framework.dispatcher.handler;

import com.google.gson.JsonObject;
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

    String IF_MODIFIED_SINCE = "If-Modified-Since";

    String LAST_MODIFIED = "Last-Modified";

    void handleResult(Object result, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;

    static void setRequestAttribute(HttpServletRequest request){
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("URL", HttpUtils.getURL(request));
        jsonObject.addProperty("Method", request.getMethod());
        jsonObject.addProperty("Request IP", HttpUtils.getRequestIP(request));
        request.setAttribute(BaseException.EXCEPTION_REQUEST, jsonObject);
    }
}
