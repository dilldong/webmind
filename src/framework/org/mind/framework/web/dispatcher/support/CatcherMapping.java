package org.mind.framework.web.dispatcher.support;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.mind.framework.annotation.Interceptor;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.web.interceptor.HandlerInterceptor;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;

/**
 * Interceptor settings
 *
 * @author dp
 * @date 2021-02-14
 */
@Setter
@Getter
public class CatcherMapping extends Catcher {
    private String[] excludesRegex;

    public CatcherMapping(Interceptor annotation, HandlerInterceptor hander) {
        super(annotation.value(), hander);
        this.order = annotation.order();
        this.excludesRegex = this.parseUriPattern(annotation.excludes());
    }

    @Override
    protected String[] parseUriPattern(String[] originUriArray) {
        int length = originUriArray.length;
        String[] uriPatterns = new String[length];

        for (int i = 0; i < length; ++i) {
            uriPatterns[i] =
                    originUriArray[i].contains(CorsConfiguration.ALL) ?
                            MatcherUtils.convertURI(originUriArray[i]) :
                            MatcherUtils.convertURIIfExists(originUriArray[i]);
        }
        return uriPatterns;
    }

    @Override
    public boolean matchOne(String value, int... flags) {
        // first excludeRegex
        if (excludesRegex != null)
            for (String exclude : excludesRegex)
                if (exclude.equals(value) || MatcherUtils.matcher(value, exclude, flags).matches())// matched
                    return false;

        // intercept regex
        return super.matchOne(value, flags);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("order", order)
                .append(" interceptor", Arrays.toString(uriRegex))
                .append(" excludes", Arrays.toString(excludesRegex))
                .toString();
    }
}
