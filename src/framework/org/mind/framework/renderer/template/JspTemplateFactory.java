package org.mind.framework.renderer.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;

/**
 * TemplateFactory which uses JSP.
 *
 * @author dp
 */
public class JspTemplateFactory extends TemplateFactory {

    private static final Logger log = LoggerFactory.getLogger(JspTemplateFactory.class);

    @Override
    public Template loadTemplate(String path) {
        log.debug("Load JSP template '{}'", path);
        return new JspTemplate(path);
    }

    @Override
    public void init(ServletContext context) {
        if (log.isDebugEnabled())
            log.debug("JspTemplateFactory init success.");
    }

}
