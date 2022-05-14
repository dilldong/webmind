package org.mind.framework;

import org.mind.framework.annotation.Interceptor;
import org.mind.framework.interceptor.AbstractHandlerInterceptor;
import org.springframework.stereotype.Component;

/**
 * @author Marcus
 * @version 1.0
 * @date 2022-05-14
 */
@Component
@Interceptor(excludes = {"/error/*"}, order = 1)
public class InterceptorClass extends AbstractHandlerInterceptor {
}
