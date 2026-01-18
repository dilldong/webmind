package org.mind.framework.web.dispatcher.support;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.apache.catalina.core.StandardContext;

/**
 * @version 1.0
 * @author Marcus
 * @date 2024/5/4
 */
public interface EventRegistration {
    void registration(ServletContext servletContext, StandardContext standardContext) throws ServletException;
}
