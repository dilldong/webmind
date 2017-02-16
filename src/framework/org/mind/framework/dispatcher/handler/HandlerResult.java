package org.mind.framework.dispatcher.handler;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 
 * @author dp
 *
 */
public interface HandlerResult {
	
	public static final String HEADER_IFMODSINCE = "If-Modified-Since";
	
	public static final String HEADER_LASTMOD = "Last-Modified";
	
	void handleResult(Object result, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;

}
