package org.mind.framework.web.container.spring;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.Resource;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/**
 * @author Marcus
 * @version 1.0
 */
public class AnnotationLoad4SpringContext extends AnnotationConfigWebApplicationContext {

    @Override
    protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) {
        // 获取到LaunchedURLClassLoader的ClassLoader，受到5.3.x安全影响，否则无法找到嵌套jar中的资源
        ClassLoader cl = AnnotationLoad4SpringContext.class.getClassLoader();

        // Sync to Context
        setClassLoader(cl);

        // Sync to Reader
        beanFactory.setBeanClassLoader(cl);

        super.loadBeanDefinitions(beanFactory);
    }

    /**
     * Rewrite getResource method
     * Will be called by tags such as <context:property-placeholder>
     */
    @NotNull
    @Override
    public Resource getResource(String location) {
        if(location.endsWith(CustomResourceLoader.PROPS_SUFFIX))
            return CustomResourceLoader.getResourceInJarIfNeed(location, AnnotationLoad4SpringContext.class.getClassLoader());

        return super.getResource(location);
    }

}
