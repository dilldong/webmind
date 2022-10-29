package org.mind.framework;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletContext;
import java.util.Objects;

public final class ContextSupport {

    private static ApplicationContext wctx;

    /**
     * Support Spring file loading
     *
     * @param configLocations spring files
     * @return
     */
    public static ApplicationContext initContext(String[] configLocations) {
        for (int i = 0; i < configLocations.length; ++i)
            if (!configLocations[i].startsWith("file:"))
                configLocations[i] = String.format("file:%s", configLocations[i]);

        wctx = new FileSystemXmlApplicationContext(configLocations);
        return wctx;
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
        wctx = WebApplicationContextUtils.getRequiredWebApplicationContext(sc);
    }

    public static void setApplicationContext(ApplicationContext applicationContext){
        wctx = applicationContext;
    }

    /**
     * Get the Spring context
     *
     * @param name
     * @return
     * @author dp
     */
    public static Object getBean(String name) {
        Objects.requireNonNull(wctx, "Spring ApplicationContext is null.");
        return wctx.getBean(name);
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
        Objects.requireNonNull(wctx, "Spring ApplicationContext is null.");
        if (Objects.isNull(requiredType))
            return (T) getBean(name);

        return wctx.<T>getBean(name, requiredType);
    }

    public <T> T getBean(Class<T> requiredType) {
        Objects.requireNonNull(wctx, "Spring ApplicationContext is null.");
        return wctx.<T>getBean(requiredType);
    }

    public <T> T getBean(Class<T> requiredType, Object... args) {
        Objects.requireNonNull(wctx, "Spring ApplicationContext is null.");
        return wctx.<T>getBean(requiredType, args);
    }

    public static String[] getBeanNames() {
        return wctx.getBeanDefinitionNames();
    }

    public static long getStartupTime() {
        return wctx.getStartupDate();
    }

}
