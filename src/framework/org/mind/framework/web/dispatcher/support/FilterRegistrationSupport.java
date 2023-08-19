package org.mind.framework.web.dispatcher.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.mind.framework.annotation.Filter;
import org.mind.framework.web.filter.AbstractHandlerFilter;
import org.mind.framework.web.filter.FilterRegistration;
import org.mind.framework.web.filter.HandlerFilter;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2023/8/11
 */
@Slf4j(topic = "FilterRegistration")
@RequiredArgsConstructor
public class FilterRegistrationSupport {

    private final WebApplicationContext applicationContext;

    public void registration(ServletContext servletContext) throws ServletException {
        Map<String, HandlerFilter> filterOfType = applicationContext.getBeansOfType(HandlerFilter.class);
        if (filterOfType.isEmpty())
            return;

        List<FilterRegistration> list = new ArrayList<>();
        filterOfType.forEach((k, v) -> {
            Class<?> clazz = v.getClass();
            if (clazz.isAnnotationPresent(Filter.class)) {
                if (AbstractHandlerFilter.class.isAssignableFrom(clazz) || HandlerFilter.class.isAssignableFrom(clazz)) {
                    Filter filter = clazz.getAnnotation(Filter.class);
                    if (ArrayUtils.isNotEmpty(filter.value())) {
                        list.add(new FilterRegistration(k, filter, v));
                    }
                }
            }
        });

        if (list.size() > 1)
            Collections.sort(list);

        list.forEach(filter -> {
            servletContext
                    .addFilter(filter.getName(), filter.getHandler())
                    .addMappingForUrlPatterns(
                            EnumSet.copyOf(filter.getDispatcherTypes()),
                            filter.isMatchAfter(),
                            filter.getUriPatterns());
            log.info("Loaded Filter: {}", filter);
        });
    }

    /*
     * @Order
     * AnnotationAwareOrderComparator.sort(contextInitializers);
     */
}
