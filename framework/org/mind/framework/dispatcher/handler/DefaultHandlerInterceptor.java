package org.mind.framework.dispatcher.handler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DefaultHandlerInterceptor implements HandlerInterceptor {

	@Override
	public boolean doBefore(Object handler, HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		
		return false;
	}

	@Override
	public boolean doAfter(Object handler, Object handlerResult,
			HttpServletRequest request, HttpServletResponse response)
			throws Exception {
		
		return false;
	}

	@Override
	public void renderCompletion(Object handler, HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		
	}

}
