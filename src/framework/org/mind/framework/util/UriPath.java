package org.mind.framework.util;

import javax.servlet.http.HttpServletRequest;

/**
 * Get the uri path in the request
 *
 * @author marcus
 */
public class UriPath {

    /**
     * Identify and return the path component (from the request URI) that
     * we will use to select an Action to dispatch with.  If no such
     * path can be identified, create an error response and return
     * <code>null</code>.
     *
     * @param request The servlet request we are processing
     */
    public static String get(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();

//		log.info("path info: "+ request.getPathInfo());
//		log.info("servlet path: "+ request.getServletPath());
//		log.info("Query string: "+ request.getQueryString());

        uri = contextPath.length() > 0 ? uri.substring(contextPath.length()) : uri;

        // check is root path
        if (uri.length() > 1) {
            uri = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        }

        // uri has sessionid
        int hasJsession = uri.indexOf(";jsessionid");
        if (hasJsession != -1)
            uri = uri.substring(0, hasJsession);

        return uri;
    }
}
