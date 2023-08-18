package org.mind.framework.web.renderer;

import org.mind.framework.web.renderer.template.TemplateFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * template engine output.
 *
 * @author dp
 */
public class TemplateRender extends Render {

    private final String path;
    private final Map<String, Object> model;

    public TemplateRender(String path) {
        this.path = path;
        this.model = new HashMap<>(5);
    }

    public TemplateRender(String path, Map<String, Object> model) {
        this.path = path;
        this.model = model;
    }

    public TemplateRender(String path, String modelKey, Object modelValue) {
        this(path);
        this.model.put(modelKey, modelValue);
    }

    @Override
    public void render(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if(log.isDebugEnabled())
            log.debug("Render path: {}", path);

        TemplateFactory.getTemplateFactory()
                .loadTemplate(path)
                .render(request, response, model);
    }

}
