package org.mind.framework;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletContext;
import java.util.Arrays;
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
        Arrays.stream(configLocations)
                .forEach(config -> {
                    if (!config.startsWith("file:"))
                        config = String.format("file:%s", config);
                });

        wctx = new FileSystemXmlApplicationContext(configLocations);
        return wctx;
    }

    /**
     * Initialize Spring WebContext when the web server container starts
     *
     * @param sc ServletContext
     * @author dongping
     */
    public static void initWebContext(ServletContext sc) {
        Objects.requireNonNull(sc, "HttpServlet ServletContext is null");
        // ContextLoaderListener
        wctx = WebApplicationContextUtils.getRequiredWebApplicationContext(sc);
    }

    /**
     * Get the Spring context
     *
     * @param name
     * @return
     * @author dongping
     */
    public static Object getBean(String name) {
        return getBean(name, null);
    }

    /**
     * Get the Spring context
     *
     * @param name
     * @param requiredType interface or an implementation class
     * @return
     * @author dongping
     */
    public static Object getBean(String name, Class<?> requiredType) {
        Objects.requireNonNull(wctx, "Spring ApplicationContext is null.");
        if (Objects.isNull(requiredType))
            return wctx.getBean(name);

        return wctx.getBean(name, requiredType);
    }

    public static String[] getBeanNames() {
        return wctx.getBeanDefinitionNames();
    }

    public static long getStartupTime() {
        return wctx.getStartupDate();
    }

}
