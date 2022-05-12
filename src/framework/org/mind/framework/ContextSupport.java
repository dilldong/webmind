package org.mind.framework;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletContext;

public final class ContextSupport {

    private static ApplicationContext wctx;

    /**
     * Support Spring file loading
     *
     * @param configLocations spring files
     * @return
     */
    public static ApplicationContext initContext(String[] configLocations) {
        for (int i = 0; i < configLocations.length; i++) {
            if (!configLocations[i].startsWith("file:")) {
                configLocations[i] = String.format("file:%s", configLocations[i]);
            }
        }

        wctx = new FileSystemXmlApplicationContext(configLocations);
        return wctx;
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
        if (wctx == null)
            throw new NullPointerException("Spring ApplicationContext is null.");

        if (requiredType == null)
            return wctx.getBean(name);

        return wctx.getBean(name, requiredType);
    }

    /**
     * Initialize Spring WebContext when the web server container starts
     *
     * @param sc ServletContext
     * @author dongping
     */
    public static void initWebContext(ServletContext sc) {
        if (sc == null)
            throw new NullPointerException("HttpServlet ServletContext is null");

        /*
         * ContextLoaderListener配置
         */
        wctx = WebApplicationContextUtils.getRequiredWebApplicationContext(sc);
    }

    public static String[] getBeanNames() {
        return wctx.getBeanDefinitionNames();
    }

}
