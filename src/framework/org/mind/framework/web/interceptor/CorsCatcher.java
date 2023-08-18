package org.mind.framework.web.interceptor;

import org.mind.framework.web.dispatcher.support.Catcher;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2023/8/13
 */
public class CorsCatcher extends Catcher {
    public CorsCatcher(String[] uriRegex, HandlerInterceptor handler) {
        super(uriRegex, handler);
    }
}
