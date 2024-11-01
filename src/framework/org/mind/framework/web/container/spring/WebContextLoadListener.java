package org.mind.framework.web.container.spring;

import org.apache.catalina.core.StandardContext;
import org.jetbrains.annotations.NotNull;
import org.mind.framework.annotation.processor.EnableCacheConfiguration;
import org.mind.framework.exception.WebServerException;
import org.mind.framework.web.dispatcher.support.EventRegistration;
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
 * The servlet container starts
 *
 * @version 1.0
 * @auther Marcus
 * @date 2023/8/11
 */
public class WebContextLoadListener extends ContextLoaderListener {

    private final StandardContext standardContext;
    public WebContextLoadListener(WebApplicationContext wac, StandardContext ctx) {
        super(wac);
        this.standardContext = ctx;
    }

    @Override
    public void contextInitialized(@NotNull ServletContextEvent event) {
        super.contextInitialized(event);

        // filter registration by Spring.
        try {
            EventRegistration registration = new FilterRegistrationSupport(getCurrentWebApplicationContext());
            registration.registration(event.getServletContext(), standardContext);
        } catch (ServletException e) {
            throw new WebServerException(e.getMessage(), e);
        }
    }

    @NotNull
    @Override
    public WebApplicationContext initWebApplicationContext(@NotNull ServletContext servletContext) {
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
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(EnableCacheConfiguration.ATTR_BEAN_NAME);
            if (beanDefinition.getRole() != BeanDefinition.ROLE_INFRASTRUCTURE)
                beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        } catch (NoSuchBeanDefinitionException ignored) {

        }

        // Manually create AOP proxy
        // Object proxy = AopProxyUtils.ultimateTargetClass(cacheConfiguration);
    }
}
