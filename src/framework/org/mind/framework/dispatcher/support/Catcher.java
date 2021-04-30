package org.mind.framework.dispatcher.support;/**
 * @author dp
 * @date 2021-02-14
 */

import org.mind.framework.annotation.Interceptor;
import org.mind.framework.interceptor.HandlerInterceptor;
import org.mind.framework.util.MatcherUtils;

import java.io.Serializable;
import java.util.Arrays;

/**
 * @author Ping
 * @date 2021-02-14
 */
public class Catcher implements Comparable<Catcher>, Serializable {
    private Interceptor annotation;

    private HandlerInterceptor hander;

    private String[] interceptorRegex;

    public Catcher() {
    }

    public Catcher(Interceptor annotation, HandlerInterceptor hander) {
        this.annotation = annotation;
        this.hander = hander;

        String[] values = annotation.value();
        interceptorRegex = new String[values.length];

        for (int i = 0; i < values.length; i++)
            this.interceptorRegex[i] = MatcherUtils.convertURI(values[i]);
    }

    public boolean matchOne(String value, int... flags) {
        for (String regex : interceptorRegex)
            if (MatcherUtils.matcher(value, regex, flags).matches())// matched
                return true;

        return false;
    }

    public Interceptor getAnnotation() {
        return annotation;
    }

    public void setAnnotation(Interceptor annotation) {
        this.annotation = annotation;
    }

    public HandlerInterceptor getHander() {
        return hander;
    }

    public void setHander(HandlerInterceptor hander) {
        this.hander = hander;
    }

    public String[] getInterceptorRegex() {
        return interceptorRegex;
    }

    public void setInterceptorRegex(String[] interceptorRegex) {
        this.interceptorRegex = interceptorRegex;
    }


    @Override
    public int compareTo(Catcher catcher) {
        return this.annotation.order() - catcher.annotation.order();
    }

    @Override
    public String toString() {
        return Arrays.toString(annotation.value());
    }
}
