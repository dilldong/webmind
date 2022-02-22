package org.mind.framework.dispatcher.handler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author dp
 */
public interface HandlerResult {

    String HEADER_IFMODSINCE = "If-Modified-Since";

    String HEADER_LASTMOD = "Last-Modified";

    void handleResult(Object result, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;

}
