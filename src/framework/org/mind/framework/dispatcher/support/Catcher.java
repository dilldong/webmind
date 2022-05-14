package org.mind.framework.dispatcher.support;/**
 * @author dp
 * @date 2021-02-14
 */

import lombok.Getter;
import lombok.Setter;
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
    @Setter
    @Getter
    private Interceptor annotation;

    @Setter
    @Getter
    private HandlerInterceptor hander;

    @Setter
    @Getter
    private String[] interceptorRegex;

    @Setter
    @Getter
    private String[] excludesRegex;

    public Catcher() {
    }

    public Catcher(Interceptor annotation, HandlerInterceptor hander) {
        this.annotation = annotation;
        this.hander = hander;

        String[] values = annotation.value();
        interceptorRegex = new String[values.length];

        for (int i = 0; i < values.length; i++)
            interceptorRegex[i] = MatcherUtils.convertURI(values[i]);

        String[] excludes = annotation.excludes();
        if (excludes.length > 0) {
            excludesRegex = new String[excludes.length];
            for (int i = 0; i < excludes.length; i++)
                excludesRegex[i] = MatcherUtils.convertURI(excludes[i]);
        }
    }

    public boolean matchOne(String value, int... flags) {
        // first excludeRegex
        if (excludesRegex != null)
            for (String exclude : excludesRegex)
                if (MatcherUtils.matcher(value, exclude, flags).matches())// matched
                    return false;

        // intercept regex
        for (String regex : interceptorRegex)
            if (MatcherUtils.matcher(value, regex, flags).matches())// matched
                return true;

        return false;
    }

    @Override
    public int compareTo(Catcher catcher) {
        return this.annotation.order() - catcher.annotation.order();
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("order = ").append(annotation.order())
                .append(", ").append(" interceptor = ").append(Arrays.toString(annotation.value()))
                .append(", ").append(" excludes = ").append(Arrays.toString(annotation.excludes()))
                .toString();
    }

}
