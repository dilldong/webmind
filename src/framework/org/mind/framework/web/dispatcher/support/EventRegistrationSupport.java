package org.mind.framework.web.dispatcher.support;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.core.StandardContext;
import org.apache.commons.lang3.Strings;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.Map;

/**
 * 此类未使用，需要手动实现事件监听，如HttpSessionListener等
 * @version 1.0
 * @author Marcus
 * @date 2023/8/11
 */
@Slf4j(topic = "EventRegistration")
@RequiredArgsConstructor
public class EventRegistrationSupport implements EventRegistration {

    private final WebApplicationContext applicationContext;

    @Override
    public void registration(ServletContext servletContext, StandardContext standardContext) throws ServletException {
        Map<String, EventListener> beansOfType = applicationContext.getBeansOfType(EventListener.class);

        if (beansOfType.isEmpty())
            return;

        List<EventListener> list = new ArrayList<>(beansOfType.size());
        beansOfType.forEach((k, v) -> {
            Class<?> clazz = v.getClass();
            if (!Strings.CS.contains(clazz.getName(), "org.springframework"))
                list.add(v);
        });

        // spring order sort
        if (list.size() > 1)
            AnnotationAwareOrderComparator.sort(list);

        list.forEach(servletContext::addListener);
    }
}
