package org.mind.framework.web.dispatcher.handler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface ResourceRequest extends HandlerResult{

    boolean checkStaticResource(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException;
}
