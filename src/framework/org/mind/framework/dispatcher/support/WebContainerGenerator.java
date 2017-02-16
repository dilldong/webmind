package org.mind.framework.dispatcher.support;

import javax.servlet.ServletConfig;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mind.framework.container.ContainerAware;
import org.mind.framework.renderer.template.JspTemplateFactory;
import org.mind.framework.renderer.template.TemplateFactory;

public final class WebContainerGenerator {
	private static final Log log = LogFactory.getLog(WebContainerGenerator.class);
	
	/**
	 * init web container (Guice or Spring) by web application define.
	 * @param config javax.servlet.ServletConfig
	 * @return org.mind.framework.container.ContainerGenerator
	 */
	public static ContainerAware initMindContainer(ServletConfig config){
		String containerName = config.getInitParameter("container");
		Object obj = null;
		
		try {
			obj = Class.forName(containerName).newInstance();
		} catch (Exception e) {}
		
		try {
			if(obj == null){
				obj = Class.forName(
						ContainerAware.class.getPackage().getName() +
						"."+ containerName.toLowerCase() + 
						"."+ containerName + 
						ContainerAware.class.getSimpleName())
						.newInstance();
			}
			
			if(obj instanceof ContainerAware)
				return (ContainerAware)obj;
			
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		
		throw new IllegalArgumentException("init param invalid.");
	}
	
	/**
	 * init Page view resolver engine by web application define.
	 * @param config javax.servlet.ServletConfig
	 * @return org.mind.framework.renderer.template.TemplateFactory
	 */
	public static TemplateFactory initTemplateFactory(ServletConfig config){
		String templateName = config.getInitParameter("template");
		if(templateName == null){
			templateName = JspTemplateFactory.class.getName();
			log.info("No template factory specified. Default to '" + templateName + "'.");
		}
		
		Object obj = null;
		try {
			obj = Class.forName(templateName).newInstance();
		} catch (Exception e) {}
		
		try {
			if(obj == null){
				obj = Class.forName(
						TemplateFactory.class.getPackage().getName() +
						"."+ templateName +  TemplateFactory.class.getSimpleName())
						.newInstance();
			}
			
			if(obj instanceof TemplateFactory)
				return (TemplateFactory)obj;
			
		} catch (Exception e) {}
		
		throw new IllegalArgumentException("init param invalid.");
	}
}
