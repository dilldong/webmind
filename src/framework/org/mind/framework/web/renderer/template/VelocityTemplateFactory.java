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

    public Template loadTemplate(String path) {
        if(log.isDebugEnabled())
            log.debug("Load velocity template '{}'", path);

        return new VelocityTemplate(
                ContextSupport.getBean("velocityEngine", VelocityEngine.class).getTemplate(path));
    }

    public void init(ServletContext context) {
        if (log.isDebugEnabled())
            log.debug("VelocityTemplateFactory init success.");
    }
}
