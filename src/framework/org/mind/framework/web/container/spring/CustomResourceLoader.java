package org.mind.framework.web.container.spring;

import lombok.extern.slf4j.Slf4j;
import org.mind.framework.util.JarFileUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.Objects;

/**
 * @author: Marcus
 * @date: 2026/1/20
 * @version: 1.0
 */
@Slf4j
public final class CustomResourceLoader {
    public static final String PROPS_SUFFIX = ".properties";
    public static final String XML_SUFFIX = ".xml";

    /**
     * Only handle properties
     */
    public static Resource getResourceInJarIfNeed(String location, ClassLoader classLoader) {
        // 1.Spring's default resolver
        Resource resource = new PathMatchingResourcePatternResolver(classLoader).getResource(location);
        if (resource.exists())
            return resource;

        // 2. All non-properties are rejected
        if (!(location.endsWith(PROPS_SUFFIX) || location.endsWith(XML_SUFFIX)))
            throw new IllegalArgumentException("CustomPropertiesResourceLoader only supports .properties or .xml: " + location);

        // 3. jar 内兜底加载
        log.debug("Loading resource from jar: {}", location);
        return Objects.requireNonNull(JarFileUtils.loadResourceFromJar(location), "Not found resource " + location);
    }
}
