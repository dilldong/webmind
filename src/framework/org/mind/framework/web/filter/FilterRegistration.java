package org.mind.framework.web.filter;

import jakarta.servlet.DispatcherType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.mind.framework.annotation.Filter;

import java.util.Arrays;
import java.util.List;

/**
 * @version 1.0
 * @author Marcus
 * @date 2023/8/11
 */
@Getter
@Setter
@NoArgsConstructor
public class FilterRegistration implements Comparable<FilterRegistration> {
    private String name;

    private int order;

    private boolean matchAfter;

    private List<DispatcherType> dispatcherTypes;

    private HandlerFilter handler;

    private String[] uriPatterns;

    public FilterRegistration(String name, Filter annotation, HandlerFilter handler) {
        this.name = name;
        this.handler = handler;
        this.order = annotation.order();
        this.uriPatterns = annotation.value();
        this.matchAfter = annotation.matchAfter();
        this.dispatcherTypes = Arrays.asList(annotation.dispatcherTypes());
    }

    @Override
    public int compareTo(FilterRegistration registration) {
        return this.order - registration.order;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                .append("uriPatterns", uriPatterns)
                .append(" dispatcherTypes", dispatcherTypes)
                .append(" order", order)
                .append(" matchAfter", matchAfter)
                .toString();
    }
}
