package org.mind.framework.renderer.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;

/**
 * TemplateFactory which holds the singleton instance of TemplateFactory.
 * 
 * @author dp
 */
public abstract class TemplateFactory {

	private static final Logger log = LoggerFactory.getLogger(TemplateFactory.class);
	
	private static TemplateFactory tf;
	
	/**
	 * init Template-Factory with Web container startup.
	 * @param instanceFactory
	 */
	public static void setTemplateFactory(TemplateFactory instanceFactory){
		tf = instanceFactory;
		log.info("Template factory is: {}", tf.getClass().getName());
	}
	
	public static TemplateFactory getTemplateFactory(){
		return tf;
	}

    /**
     * Init TemplateFactory.
     */
    public abstract void init(ServletContext context);

    /**
     * Load Template from path.
     * 
     * @param path Template file path, relative with webapp's root path.
     * @return Template instance.
     * @throws Exception If load failed, e.g., file not found.
     */
    public abstract Template loadTemplate(String path);

}
