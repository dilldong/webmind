package org.mind.framework.renderer.template;

import javax.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * Velocity Template factory.
 * 
 * @author dp
 */
public class VelocityTemplateFactory extends TemplateFactory {

	private static final Log log = LogFactory.getLog(VelocityTemplateFactory.class);

	private VelocityEngine velocityEngine;

	public Template loadTemplate(String path)  {
		if (log.isDebugEnabled())
			log.debug("Load Velocity template '" + path + "'.");
		
		return new VelocityTemplate(velocityEngine.getTemplate(path));
	}

	public void init(ServletContext context) {
		WebApplicationContext wctx = 
				WebApplicationContextUtils.getRequiredWebApplicationContext(context);
		
		this.velocityEngine = 
				(VelocityEngine)
				wctx.getBean("velocityEngine", VelocityEngine.class);
		
	}
}
