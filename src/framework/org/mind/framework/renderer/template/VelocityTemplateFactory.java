package org.mind.framework.renderer.template;

import org.apache.velocity.app.VelocityEngine;
import org.mind.framework.ContextSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletContext;

/**
 * Velocity Template factory.
 *
 * @author dp
 */
public class VelocityTemplateFactory extends TemplateFactory {

    private static final Logger log = LoggerFactory.getLogger(VelocityTemplateFactory.class);

    private VelocityEngine velocityEngine;

    public Template loadTemplate(String path) {
        log.debug("Load Velocity template '{}'", path);

        return new VelocityTemplate(velocityEngine.getTemplate(path));
    }

    public void init(ServletContext context) {
        WebApplicationContext wctx =
                WebApplicationContextUtils.getRequiredWebApplicationContext(context);

        this.velocityEngine =
                (VelocityEngine)
                        ContextSupport.getBean("velocityEngine", VelocityEngine.class);

    }
}
