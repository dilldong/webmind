package org.mind.framework.web.renderer.template;

import jakarta.servlet.ServletContext;

/**
 * TemplateFactory which uses JSP.
 *
 * @author dp
 */
public class JspTemplateFactory extends TemplateFactory {

    @Override
    public Template loadTemplate(String path) {
        if(log.isDebugEnabled())
            log.debug("Load JSP template '{}'", path);
        return new JspTemplate(path);
    }

    @Override
    public void init(ServletContext context) {
        if (log.isDebugEnabled())
            log.debug("JspTemplateFactory init success.");
    }

}
