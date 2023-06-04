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
 */
public class XmlLoad4SpringContext extends XmlWebApplicationContext {

    private static final Logger log = LoggerFactory.getLogger(XmlLoad4SpringContext.class);

    @Override
    protected void initBeanDefinitionReader(XmlBeanDefinitionReader beanDefinitionReader) {
        super.initBeanDefinitionReader(beanDefinitionReader);
    }

    @Override
    protected void loadBeanDefinitions(XmlBeanDefinitionReader reader) throws IOException {
        final String[] configLocations = super.getConfigLocations();
        if (configLocations == null)
            return;

        boolean showLog = false;
        for (String location : configLocations) {
            Resource resource = new ClassPathResource(location, Thread.currentThread().getContextClassLoader());

            // xml in jar
            if (!resource.exists()) {
                String tempLoc = location.startsWith(org.mind.framework.util.IOUtils.DIR_SEPARATOR) ? location.substring(1) : location;
                InputStream in = JarFileUtils.getJarEntryStream(
                        WebServerConfig.JAR_IN_CLASSES + IOUtils.DIR_SEPARATOR_UNIX + tempLoc);

                showLog = true;
                reader.setValidating(false);// close validate
                resource = new InputStreamResource(in);
            }

            reader.loadBeanDefinitions(resource);
        }

        if (showLog)
            log.info("Loading spring XML: {}", Arrays.toString(configLocations));
    }

    @Override
    protected void doClose() {
        super.doClose();
        if (log.isInfoEnabled() && !log.isDebugEnabled())
            log.info("Closing {}", super.toString());
    }
}
