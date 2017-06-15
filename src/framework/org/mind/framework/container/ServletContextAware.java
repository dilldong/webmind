package org.mind.framework.container;

import javax.servlet.ServletContext;

/**
 * Guice module which implements this interface will automatically get the
 * ServletContext object in web application.
 *
 * @author dp
 */
public interface ServletContextAware {

    /**
     * Called by GuiceContainerFactory when initialize module.
     *
     * @param servletContext ServletContext object to be used by this object
     */
    void setServletContext(ServletContext servletContext);

}
