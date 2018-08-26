package org.mind.framework.renderer.template;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.ServletContext;

/**
 * TemplateFactory which uses JSP.
 * 
 * @author Michael Liao (askxuefeng@gmail.com)
 */
public class JspTemplateFactory extends TemplateFactory {

    private Log log = LogFactory.getLog(getClass());

    @Override
    public Template loadTemplate(String path){
        if (log.isDebugEnabled())
            log.debug("Load JSP template '" + path + "'.");
        return new JspTemplate(path);
    }

    @Override
    public void init(ServletContext context) {
        log.info("JspTemplateFactory init success.");
    }

}
