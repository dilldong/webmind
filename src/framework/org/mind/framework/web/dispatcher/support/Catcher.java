package org.mind.framework.web.dispatcher.support;

import lombok.Getter;
import lombok.Setter;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.web.interceptor.HandlerInterceptor;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2023/8/13
 */
@Getter
@Setter
public class Catcher implements Comparable<Catcher> {
    protected final HandlerInterceptor handler;

    protected int order;

    protected String[] uriRegex;

    public Catcher(HandlerInterceptor handler) {
        this.handler = handler;
    }

    public Catcher(String[] uriRegex, HandlerInterceptor handler) {
        this(handler);
        this.setUriRegex(uriRegex);
    }

    public void setUriRegex(String[] uriRegex) {
        this.uriRegex = this.parseUriPattern(uriRegex);
    }

    public boolean matchOne(String value, int... flags) {
        // intercept regex
        for (String regex : this.uriRegex)
            if (regex.equals(value) || MatcherUtils.matcher(value, regex, flags).matches())// matched
                return true;

        return false;
    }

    protected String[] parseUriPattern(String[] originUriArray){
        int length = originUriArray.length;
        String[] uriPatterns = new String[length];

        for (int i = 0; i < length; ++i)
            uriPatterns[i] = MatcherUtils.convertURIIfExists(originUriArray[i]);
        return uriPatterns;
    }


    @Override
    public int compareTo(Catcher catcher) {
        return this.order - catcher.order;
    }
}
