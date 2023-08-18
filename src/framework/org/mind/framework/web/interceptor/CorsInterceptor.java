package org.mind.framework.web.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.mind.framework.ContextSupport;
import org.mind.framework.util.HttpUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsProcessor;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.DefaultCorsProcessor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2023/8/13
 */
@Slf4j
public class CorsInterceptor extends AbstractHandlerInterceptor {

    private final CorsConfiguration config;

    private CorsProcessor processor;

    public CorsInterceptor(CorsConfiguration config) {
        this.config = config;
        try {
            processor = ContextSupport.getBean(DefaultCorsProcessor.class);
        } catch (NoSuchBeanDefinitionException e) {
            processor = new CorsRegexProcessor();
        }
    }

    @Override
    public boolean doBefore(HttpServletRequest request, HttpServletResponse response) {
        boolean isValid;
        try {
            isValid = this.processor.processRequest(config, request, response);
        } catch (IOException e) {
            log.error("[{}] - Cors processor filter an exception: {}", HttpUtils.getURI(request, true), e.getMessage());
            return false;
        }

        /*
         * 浏览器将CORS请求分成两类：简单请求和非简单请求。
         * 浏览器对这两种请求的处理是不一样的，
         * 非简单请求的CORS请求，会在正式通信之前，增加一次HTTP查询请求，称为"预检"请求（preflight）
         */
        if (isValid && !CorsUtils.isPreFlightRequest(request))
            return super.doBefore(request, response);

        return false;
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                .append("origins", config.getAllowedOrigins());

        if (!(Objects.isNull(config.getAllowedMethods()) && config.getAllowedMethods().isEmpty()))
            builder.append(" methods", config.getAllowedMethods());

        if (!(Objects.isNull(config.getAllowedHeaders()) && config.getAllowedHeaders().isEmpty()))
            builder.append(" headers", config.getAllowedHeaders());

        builder.append(" credentials", BooleanUtils.isTrue(config.getAllowCredentials()))
                .append(" maxAge", config.getMaxAge());

        return builder.toString();
    }
}
