package org.mind.framework.annotation;

import java.util.Arrays;
import java.util.List;

import org.mind.framework.dispatcher.handler.HandlerInterceptor;

public abstract class AbstractHandlerMapping {
	
	private int order = Integer.MAX_VALUE;  // default: same as non-Ordered
	
	private String names;

	private List<HandlerInterceptor> interceptors;
	
//	private WebApplicationContext wctx;

	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	public String getNames() {
		return names;
	}

	public void setNames(String names) {
		this.names = names;
	}

	public void setInterceptors(HandlerInterceptor[] interceptors) {
		this.interceptors = Arrays.asList(interceptors);
	}
	
	protected void initInterceptors() {
		if(this.interceptors == null || this.interceptors.isEmpty())
			return;
		
	}
}
