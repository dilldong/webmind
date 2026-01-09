package org.mind.framework.web.renderer;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.context.Context;
import org.mind.framework.web.renderer.template.TemplateFactory;
import org.mind.framework.web.renderer.template.VelocityTemplate;

import java.io.IOException;
import java.io.StringWriter;
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
        this.model = new HashMap<>(8);
    }

    public TemplateRender(String path, Map<String, Object> model) {
        this.path = path;
        this.model = model;
    }

    public TemplateRender(String path, String modelKey, Object modelValue) {
        this(path);
        this.model.put(modelKey, modelValue);
    }

    public String renderString() {
        VelocityTemplate template =
                (VelocityTemplate) TemplateFactory.getTemplateFactory().loadTemplate(path);


        // init context:
        Context context = new VelocityContext(model);

        // render:
        StringWriter writer = new StringWriter();
        template.getTemplate().merge(context, writer);

        return writer.toString();
    }

    @Override
    public void render(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (log.isDebugEnabled())
            log.debug("Render path: {}", path);

        TemplateFactory.getTemplateFactory()
                .loadTemplate(path)
                .render(request, response, model);
    }

}
