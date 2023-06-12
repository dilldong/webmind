package org.mind.framework;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletContext;
import java.util.Objects;

public final class ContextSupport {

    private static volatile ApplicationContext applicationContext;

    /**
     * Support Spring file loading
     *
     * @param configLocations spring files
     * @return
     */
    public static ApplicationContext initContextByFile(String ... configLocations) {
        for (int i = 0; i < configLocations.length; ++i)
            if (!configLocations[i].startsWith("file:"))
                configLocations[i] = String.format("file:%s", configLocations[i]);

        setApplicationContext(new FileSystemXmlApplicationContext(configLocations));
        return applicationContext;
    }

    public static ApplicationContext initContextByClassPathFile(String ... configLocations) {
        setApplicationContext(new ClassPathXmlApplicationContext(configLocations));
        return applicationContext;
    }

    /**
     * Initialize Spring WebContext when the web server container starts
     *
     * @param sc ServletContext
     * @author dp
     */
    public static void initWebContext(ServletContext sc) {
        Objects.requireNonNull(sc, "HttpServlet ServletContext is null");
        // ContextLoaderListener
        applicationContext = WebApplicationContextUtils.getRequiredWebApplicationContext(sc);
    }

    public static void setApplicationContext(ApplicationContext applicationContext){
        ContextSupport.applicationContext = applicationContext;
    }

    /**
     * Get the Spring context
     *
     * @param name
     * @return
     * @author dp
     */
    public static Object getBean(String name) {
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

    public <T> T getBean(Class<T> requiredType) {
        Objects.requireNonNull(applicationContext, "Spring ApplicationContext is null.");
        return applicationContext.getBean(requiredType);
    }

    public <T> T getBean(Class<T> requiredType, Object... args) {
        Objects.requireNonNull(applicationContext, "Spring ApplicationContext is null.");
        return applicationContext.getBean(requiredType, args);
    }

    public static String[] getBeanNames() {
        return applicationContext.getBeanDefinitionNames();
    }

    public static long getStartupTime() {
        return applicationContext.getStartupDate();
    }

}
