package org.mind.framework.util;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;

import java.io.IOException;
import java.util.Objects;

/**
 * For this usage:<br> @PropertySource(value = "classpath:application.yml", factory = YamlPropertyFactory.class)
 *
 * @version 1.0
 * @author Marcus
 * @date 2023/7/9
 */
public class YamlPropertyFactory extends DefaultPropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(@Nullable String name, @NotNull EncodedResource resource) throws IOException {
        if (Objects.isNull(name))
            return super.createPropertySource(null, resource);

        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(resource.getResource());
        factory.afterPropertiesSet();

        String sourceName = StringUtils.defaultIfEmpty(name, resource.getResource().getFilename());
        return new PropertiesPropertySource(sourceName, factory.getObject());
    }
}
