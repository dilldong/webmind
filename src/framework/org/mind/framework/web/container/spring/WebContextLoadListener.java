package org.mind.framework.web.container.spring;

import org.apache.catalina.Context;
import org.jetbrains.annotations.NotNull;
import org.mind.framework.annotation.EanbleCacheConfiguration;
import org.mind.framework.exception.WebServerException;
import org.mind.framework.web.dispatcher.support.FilterRegistrationSupport;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2023/8/11
 */
public class WebContextLoadListener extends ContextLoaderListener {

    private final Context context;

    public WebContextLoadListener(WebApplicationContext wac, Context context) {
        super(wac);
        this.context = context;
    }

    @Override
    public void contextInitialized(@NotNull ServletContextEvent event) {
        super.contextInitialized(event);

        // Filter registration by spring loaded.
        try {
            new FilterRegistrationSupport(getCurrentWebApplicationContext()).registration(context.getServletContext());
        } catch (ServletException e) {
            throw new WebServerException(e.getMessage(), e);
        }
    }

    @NotNull
    @Override
    public WebApplicationContext initWebApplicationContext(ServletContext servletContext) {
        WebApplicationContext webAppContext = super.initWebApplicationContext(servletContext);

        if (webAppContext instanceof ConfigurableWebApplicationContext)
            registerCustomBean((ConfigurableWebApplicationContext) webAppContext);

        return webAppContext;
    }

    /**
     * Register custom bean in to Spring
     */
    private void registerCustomBean(ConfigurableWebApplicationContext applicationContext) {
        ConfigurableListableBeanFactory beanFactory = applicationContext.getBeanFactory();
        try {
            beanFactory.getBean(EanbleCacheConfiguration.ATTR_BEAN_NAME, EanbleCacheConfiguration.class);
        } catch (NoSuchBeanDefinitionException ignored) {
            throw new IllegalStateException("Cannot register 'EanbleCacheConfiguration' to Spring.");
        }

        try {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(EanbleCacheConfiguration.ATTR_BEAN_NAME);
            if (beanDefinition.getRole() != BeanDefinition.ROLE_INFRASTRUCTURE)
                beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        } catch (NoSuchBeanDefinitionException ignored) {}

        // Manually create AOP proxy
        // Object proxy = AopProxyUtils.ultimateTargetClass(cacheConfiguration);
    }
}
