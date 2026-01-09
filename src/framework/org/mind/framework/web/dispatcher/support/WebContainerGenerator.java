package org.mind.framework.web.dispatcher.support;

import jakarta.servlet.ServletConfig;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.web.container.ContainerAware;
import org.mind.framework.web.renderer.template.JspTemplateFactory;
import org.mind.framework.web.renderer.template.TemplateFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class WebContainerGenerator {
    private static final Logger log = LoggerFactory.getLogger(WebContainerGenerator.class);

    /**
     * init web container (Guice or Spring) by web application define.
     *
     * @param config jakarta.servlet.ServletConfig
     * @return org.mind.framework.container.ContainerGenerator
     */
    public static ContainerAware initMindContainer(ServletConfig config) {
        String containerName = config.getInitParameter("container");
        Object obj = null;

        try {
            obj = ClassUtils.newInstance(containerName);
        } catch (Exception ignored) {
        }

        try {
            if (Objects.isNull(obj)) {
                obj = ClassUtils.newInstance(
                        String.format("%s.%s.%s%s",
                                ContainerAware.class.getPackage().getName(),
                                containerName.toLowerCase(),
                                containerName,
                                ContainerAware.class.getSimpleName()));
            }

            if (obj instanceof ContainerAware)
                return (ContainerAware) obj;

        } catch (Exception e) {
            ThrowProvider.doThrow(e);
        }

        throw new IllegalArgumentException("init param invalid.");
    }

    /**
     * init Page view resolver engine by web application define.
     *
     * @param config jakarta.servlet.ServletConfig
     * @return org.mind.framework.renderer.template.TemplateFactory
     */
    public static TemplateFactory initTemplateFactory(ServletConfig config) {
        String templateName = config.getInitParameter("template");
        if (StringUtils.isEmpty(templateName) || "JspTemplate".equalsIgnoreCase(templateName)) {
            templateName = JspTemplateFactory.class.getName();
            if(log.isDebugEnabled())
                log.debug("Default template factory to '{}'.", templateName);
        }

        Object obj = null;
        try {
            obj = ClassUtils.newInstance(templateName);
        } catch (Exception ignored) {
        }

        if (Objects.isNull(obj)) {
            try {
                obj = ClassUtils.newInstance(
                        String.format("%s.%s%s",
                                TemplateFactory.class.getPackage().getName(),
                                templateName,
                                TemplateFactory.class.getSimpleName()));
            } catch (Exception e) {
                log.warn("Init template failed: {}", e.getMessage());
            }
        }

        if (obj instanceof TemplateFactory)
            return (TemplateFactory) obj;

        throw new IllegalArgumentException("Init template name invalid. Optional as Velocity or JspTemplate.");
    }
}
