package org.mind.framework.web.container.spring;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.xml.DefaultNamespaceHandlerResolver;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.Resource;
import org.springframework.web.context.support.XmlWebApplicationContext;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Marcus
 * @version 1.0
 */
public class XmlLoad4SpringContext extends XmlWebApplicationContext {

    private static final Logger log = LoggerFactory.getLogger(XmlLoad4SpringContext.class);

    private final AtomicBoolean closeing;

    public XmlLoad4SpringContext() {
        this.closeing = new AtomicBoolean(false);
    }

    @Override
    protected void loadBeanDefinitions(@NotNull XmlBeanDefinitionReader reader) {
        // 获取到LaunchedURLClassLoader的ClassLoader，受到5.3.x安全影响，否则无法找到嵌套jar中的资源
        ClassLoader cl = XmlLoad4SpringContext.class.getClassLoader();

        // Sync to Context
        setClassLoader(cl);

        // Sync to Reader
        reader.setBeanClassLoader(cl);
        reader.setResourceLoader(this);

        // Set NamespaceHandlerResolver
        reader.setNamespaceHandlerResolver(new DefaultNamespaceHandlerResolver(cl));

        String[] configLocations = super.getConfigLocations();
        if (ArrayUtils.isEmpty(configLocations))
            return;

        // close DTD validate
        reader.setValidating(false);

        for (String location : configLocations) {
            Resource resource = CustomResourceLoader.getResourceInJarIfNeed(location, cl);
            if(Objects.isNull(resource)) {
                log.warn("Skip resource(Not-found): {}", location);
                continue;
            }

            reader.loadBeanDefinitions(resource);
        }

        log.info("Loading spring XML: {}", Arrays.toString(configLocations));
    }

    /**
     * Rewrite getResource method
     * Will be called by tags such as <context:property-placeholder>
     */
    @NotNull
    @Override
    public Resource getResource(String location) {
        if(location.endsWith(CustomResourceLoader.PROPS_SUFFIX))
            return CustomResourceLoader.getResourceInJarIfNeed(location, XmlLoad4SpringContext.class.getClassLoader());

        return super.getResource(location);
    }


    @Override
    protected void doClose() {
        if (this.closeing.compareAndSet(false, true)) {
            super.doClose();
            if (log.isInfoEnabled() && !log.isDebugEnabled())
                log.info("Closing {}", super.toString());
        }
    }
}
