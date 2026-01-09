package org.mind.framework.web.dispatcher.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public interface ResourceRequest extends HandlerResult{

    boolean checkStaticResource(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException;
}
