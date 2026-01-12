package org.mind.framework.web.interceptor;

import org.apache.commons.lang3.StringUtils;
import org.mind.framework.util.MatcherUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.util.Objects;

/**
 * @version 1.0
 * @author Marcus
 * @date 2023/8/18
 */
public class CorsRegexProcessor extends DefaultCorsProcessor {

    @Override
    protected String checkOrigin(CorsConfiguration config, String requestOrigin) {
        if (StringUtils.isEmpty(requestOrigin)
                || Objects.isNull(config.getAllowedOrigins())
                || config.getAllowedOrigins().isEmpty()) {
            return null;
        }

        for (String allowedOrigin : config.getAllowedOrigins()) {
            if (CorsConfiguration.ALL.equals(allowedOrigin)) {
                if (!Boolean.TRUE.equals(config.getAllowCredentials()))
                    return CorsConfiguration.ALL;
                return requestOrigin;
            }

            // Regex pattern match: (CrossOrigin(origins = {"*.abc.com"}) )
            if (MatcherUtils.matchURL(allowedOrigin, requestOrigin))
                return requestOrigin;
        }
        return null;
    }
}
