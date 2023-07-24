package org.mind.framework.renderer.template;

import org.apache.velocity.app.VelocityEngine;
import org.mind.framework.ContextSupport;

import javax.servlet.ServletContext;

/**
 * Velocity Template factory.
 *
 * @author dp
 */
public class VelocityTemplateFactory extends TemplateFactory {

    private VelocityEngine velocityEngine;

    public Template loadTemplate(String path) {
        if(log.isDebugEnabled())
            log.debug("Load velocity template '{}'", path);
        return new VelocityTemplate(velocityEngine.getTemplate(path));
    }

    public void init(ServletContext context) {
        this.velocityEngine = ContextSupport.getBean("velocityEngine", VelocityEngine.class);
    }
}
