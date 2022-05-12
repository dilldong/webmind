package org.mind.framework.server;

import org.apache.commons.io.IOUtils;
import org.mind.framework.util.JarFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.web.context.support.XmlWebApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * @author Marcus
 * @version 1.0
 * @date 2022-05-12
 */
public class XmlLoadForSpringContext extends XmlWebApplicationContext {

    private static final Logger log = LoggerFactory.getLogger(XmlLoadForSpringContext.class);

    @Override
    protected void initBeanDefinitionReader(XmlBeanDefinitionReader beanDefinitionReader) {
        super.initBeanDefinitionReader(beanDefinitionReader);
        beanDefinitionReader.setValidating(false);
    }

    @Override
    protected void loadBeanDefinitions(XmlBeanDefinitionReader reader) throws IOException {
        final String[] configLocations = super.getConfigLocations();
        if (configLocations == null)
            return;

        boolean showLog = false;
        for (String location : configLocations) {
            Resource resource = new ClassPathResource(location, reader.getBeanClassLoader());
            if (!resource.exists()) {
                showLog = true;
                String tempLoc = location.startsWith("/") ? location.substring(1) : location;
                InputStream in = JarFileUtils.getJarEntryStream(
                        String.format("%s%c%s", WebServer.JAR_IN_CLASSES, IOUtils.DIR_SEPARATOR_UNIX, tempLoc));

                resource = new InputStreamResource(in);
            }

            reader.loadBeanDefinitions(resource);
        }

        if (showLog)
            log.info("Loading spring XML: {}", Arrays.toString(configLocations));
    }

}
