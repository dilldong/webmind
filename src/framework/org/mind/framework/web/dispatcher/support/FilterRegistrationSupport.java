package org.mind.framework.web.dispatcher.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.core.StandardContext;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.mind.framework.annotation.Filter;
import org.mind.framework.web.filter.FilterRegistration;
import org.mind.framework.web.filter.HandlerFilter;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Filter event registration
 *
 * @version 1.0
 * @author Marcus
 * @date 2023/8/11
 */
@Slf4j(topic = "FilterRegistration")
@RequiredArgsConstructor
public class FilterRegistrationSupport implements EventRegistration {

    private final WebApplicationContext applicationContext;

    public void registration(ServletContext servletContext, StandardContext standardContext) throws ServletException {
        Map<String, HandlerFilter> filterOfType = applicationContext.getBeansOfType(HandlerFilter.class);
        if (filterOfType.isEmpty())
            return;

        List<FilterRegistration> list = new ArrayList<>(filterOfType.size());
        filterOfType.forEach((k, v) -> {
            Class<?> clazz = v.getClass();
            if (clazz.isAnnotationPresent(Filter.class)) {
                Filter filter = clazz.getAnnotation(Filter.class);
                if (ArrayUtils.isNotEmpty(filter.value()))
                    list.add(new FilterRegistration(k, filter, v));
            }
        });

        // order sort
        if (list.size() > 1)
            Collections.sort(list);

        list.forEach(filter -> {
            // 该方法适用于servlet3.0以下规范
//            servletContext
//                    .addFilter(filter.getName(), filter.getHandler())
//                    .addMappingForUrlPatterns(
//                            EnumSet.copyOf(filter.getDispatcherTypes()),
//                            filter.isMatchAfter(),
//                            filter.getUriPatterns());

            // 避免了Servlet 3.0规范的限制, 不能通过ServletContext来添加过滤器
            // define filter
            FilterDef filterDef = new FilterDef();
            filterDef.setFilterName(filter.getName());
            filterDef.setFilter(filter.getHandler());
            standardContext.addFilterDef(filterDef);

            // add filter mapping
            FilterMap filterMap = new FilterMap();
            filterMap.setFilterName(filterDef.getFilterName());

            filter.getDispatcherTypes().forEach(type -> filterMap.setDispatcher(type.name()));

            if (ArrayUtils.isNotEmpty(filter.getUriPatterns())) {
                for (String uri : filter.getUriPatterns())
                    filterMap.addURLPattern(uri);
            }

            if (filter.isMatchAfter())
                standardContext.addFilterMap(filterMap);
            else
                standardContext.addFilterMapBefore(filterMap);

            log.info("Loaded Filter: {}", filter);
        });
    }

    /*
     * @Order
     * AnnotationAwareOrderComparator.sort(contextInitializers);
     */
}
