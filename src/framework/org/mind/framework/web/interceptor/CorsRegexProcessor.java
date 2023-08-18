package org.mind.framework.web.interceptor;

import org.jetbrains.annotations.NotNull;
import org.mind.framework.util.MatcherUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2023/8/18
 */
public class CorsRegexProcessor extends DefaultCorsProcessor {

    @Override
    protected String checkOrigin(@NotNull CorsConfiguration config, String requestOrigin) {
        if (!StringUtils.hasText(requestOrigin))
            return null;

        if (ObjectUtils.isEmpty(config.getAllowedOrigins()))
            return null;

        if (config.getAllowedOrigins().contains(CorsConfiguration.ALL)) {
            if (config.getAllowCredentials() != Boolean.TRUE)
                return CorsConfiguration.ALL;
            return requestOrigin;
        }

        for (String allowedOrigin : config.getAllowedOrigins()) {
            if (requestOrigin.equalsIgnoreCase(allowedOrigin))
                return requestOrigin;

            // Regex pattern match: (CrossOrigin(origins = {"*.abc.com"}) )
            if (MatcherUtils.matchURL(allowedOrigin, requestOrigin))
                return requestOrigin;
        }
        return null;
    }
}
