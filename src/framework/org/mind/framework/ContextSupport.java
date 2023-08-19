package org.mind.framework;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletContext;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Objects;

public final class ContextSupport {

    private static ApplicationContext applicationContext;

    public static AbstractXmlApplicationContext initSpringByFile(String... configLocations) {
        for (int i = 0; i < configLocations.length; ++i)
            if (!configLocations[i].startsWith("file:"))
                configLocations[i] = String.format("file:%s", configLocations[i]);

        AbstractXmlApplicationContext context = new FileSystemXmlApplicationContext(configLocations);
        context.registerShutdownHook();
        setApplicationContext(context);
        return context;
    }

    public static AbstractXmlApplicationContext initSpringByClassPathFile(String... configLocations) {
        AbstractXmlApplicationContext context = new ClassPathXmlApplicationContext(configLocations);
        context.registerShutdownHook();
        setApplicationContext(context);
        return context;
    }

    public static AbstractApplicationContext initSpringByAnnotationClass(Class<?>... componentClasses) {
        AbstractApplicationContext context = new AnnotationConfigApplicationContext(componentClasses);
        context.registerShutdownHook();
        setApplicationContext(context);
        return context;
    }

    /**
     * Initialize Spring WebContext when the web server container starts
     */
    public static WebApplicationContext initSpringByServlet(ServletContext sc) {
        Objects.requireNonNull(sc, "HttpServlet ServletContext is null");
        // ContextLoaderListener
        WebApplicationContext context = WebApplicationContextUtils.getRequiredWebApplicationContext(sc);
        setApplicationContext(context);
        return context;
    }

    public static void setApplicationContext(ApplicationContext applicationContext) {
        synchronized (ContextSupport.class) {
            if (Objects.isNull(ContextSupport.applicationContext))
                ContextSupport.applicationContext = applicationContext;
        }
    }

    /**
     * Get the Spring context
     *
     * @param name
     * @return
     * @author dp
     */
    public static Object getBean(String name) {
        if (StringUtils.isEmpty(name))
            throw new NullPointerException("Get bean 'name' must not be null");

        Objects.requireNonNull(applicationContext, "Spring ApplicationContext is null.");
        return applicationContext.getBean(name);
    }

    /**
     * Get the Spring context
     *
     * @param name
     * @param requiredType interface or an implementation class
     * @return
     * @author dp
     */
    public static <T> T getBean(String name, Class<T> requiredType) {
        if (Objects.isNull(requiredType))
            return (T) getBean(name);

        Objects.requireNonNull(applicationContext, "Spring ApplicationContext is null.");
        return applicationContext.getBean(name, requiredType);
    }

    public static <T> T getBean(Class<T> requiredType) {
        Objects.requireNonNull(applicationContext, "Spring ApplicationContext is null.");
        return applicationContext.getBean(requiredType);
    }

    public static <T> T getBean(Class<T> requiredType, Object... args) {
        Objects.requireNonNull(applicationContext, "Spring ApplicationContext is null.");
        return applicationContext.getBean(requiredType, args);
    }

    public static <T> Map<String, T> getBeans(Class<T> requiredType) {
        return applicationContext.getBeansOfType(requiredType);
    }

    public static Map<String, Object> getBeansByAnnotation(Class<? extends Annotation> annotationType) {
        return applicationContext.getBeansWithAnnotation(annotationType);
    }

    public static String[] getBeanNames() {
        return applicationContext.getBeanDefinitionNames();
    }

    public static long getStartupTime() {
        return applicationContext.getStartupDate();
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

}
