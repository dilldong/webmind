package org.mind.framework.web.renderer.template;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.util.ExtProperties;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.util.JarFileUtils;
import org.mind.framework.web.server.WebServerConfig;

import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.Objects;

/**
 * Compatible with reading in File and Jar modes.
 *
 * @version 1.0
 * @auther Marcus
 * @date 2022/8/6
 */
public class TemplateResourceLoader extends ClasspathResourceLoader {

    private String templatePath;

    @Override
    public void init(ExtProperties configuration) {
        templatePath = configuration.getString("path", org.mind.framework.util.IOUtils.DIR_SEPARATOR);
    }

    @Override
    public Reader getResourceReader(String templateName, String encoding) throws ResourceNotFoundException {
        if (StringUtils.isEmpty(templateName))
            throw new ResourceNotFoundException("No template name provided");

        String resourceName = templatePath + templateName;
        Reader result = null;
        InputStream rawStream = null;
        URL url = ClassUtils.getResource(this.getClass(), resourceName);

        try {
            if (Objects.nonNull(url))
                rawStream = ClassUtils.getResourceAsStream(this.getClass(), resourceName);
            else
                rawStream = JarFileUtils.getJarEntryStream(WebServerConfig.JAR_IN_CLASSES + resourceName);

            if (Objects.nonNull(rawStream ))
                result = this.buildReader(rawStream, encoding);
        } catch (Exception e) {
            IOUtils.closeQuietly(rawStream);
            throw new ResourceNotFoundException(
                    "TemplateResourceLoader problem with template: " + resourceName,
                    e,
                    this.rsvc.getLogContext().getStackTrace());
        }

        if (Objects.isNull(result)) {
            throw new ResourceNotFoundException(
                    "TemplateResourceLoader Error: cannot find resource " + resourceName,
                    null,
                    this.rsvc.getLogContext().getStackTrace());
        }

        return result;
    }

}
