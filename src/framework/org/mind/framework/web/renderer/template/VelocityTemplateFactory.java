package org.mind.framework.web.renderer.template;

import jakarta.servlet.ServletContext;
import org.apache.velocity.app.VelocityEngine;
import org.mind.framework.ContextSupport;

/**
 * Velocity Template factory.
 *
 * @author dp
 */
public class VelocityTemplateFactory extends TemplateFactory {

    @Override
    public Template loadTemplate(String path) {
        log.debug("Load velocity template '{}'", path);

        return new VelocityTemplate(
                ContextSupport.getBean("velocityEngine", VelocityEngine.class).getTemplate(path));
    }

    @Override
    public void init(ServletContext context) {
        log.debug("VelocityTemplateFactory init success.");
    }
}
