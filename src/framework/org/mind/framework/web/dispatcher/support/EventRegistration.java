package org.mind.framework.web.dispatcher.support;

import org.jetbrains.annotations.NotNull;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2024/5/4
 */
public interface EventRegistration {
    void registration(@NotNull ServletContext servletContext) throws ServletException;
}
