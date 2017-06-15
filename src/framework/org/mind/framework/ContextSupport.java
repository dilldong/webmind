package org.mind.framework;

import javax.servlet.ServletContext;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

public final class ContextSupport {

    /**
     * 获取spring上下文管理的java对象
     *
     * @param name
     * @return
     * @author dongping
     */
    public static Object getBean(String name) {
        return getBean(name, null);
    }

    /**
     * 获取spring上下文管理的java对象
     *
     * @param name
     * @param requiredType 可以是interface或实现类
     * @return
     * @author dongping
     */
    public static Object getBean(String name, Class<?> requiredType) {
        if (wctx == null)
            throw new NullPointerException("Spring WebApplicationContext is null.");

        if (requiredType == null)
            return wctx.getBean(name);

        return wctx.getBean(name, requiredType);
    }

    /**
     * 该方法是提供给系统使用，待server容器启动时初始化Spring WebContext。
     *
     * @param sc ServletContext
     * @author dongping
     */
    public static void initSpringContext(ServletContext sc) {
        if (sc == null)
            throw new NullPointerException("HttpServlet ServletContext is null");


		/*
		 * 这里获取spring上下文多出了一个属性字段，ContextLoaderPlugIn.SERVLET_CONTEXT_PREFIX,
		 * 这是因为通过struts的<plug-in/>配置了sping容器，和ContextLoaderListener处理一样，
		 * 将容器放到servlet容器，但是这个时候的的key值正是这个，而不是默认的
		 * WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUT
		 */
//		wctx = WebApplicationContextUtils.getWebApplicationContext(
//				sc, 
//				ContextLoaderPlugIn.SERVLET_CONTEXT_PREFIX);
		
		
		/*
		 * ContextLoaderListener配置
		 */
        wctx = WebApplicationContextUtils.getRequiredWebApplicationContext(sc);

    }

    public static String[] getBeanNames() {
        return wctx.getBeanDefinitionNames();
    }

    private static WebApplicationContext wctx;
}
