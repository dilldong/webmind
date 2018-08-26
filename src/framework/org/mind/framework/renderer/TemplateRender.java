package org.mind.framework.renderer;

import org.mind.framework.renderer.template.TemplateFactory;

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

    private String path;
    private Map<String, Object> model;

    public TemplateRender(String path) {
        this.path = path;
        this.model = new HashMap<String, Object>();
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
        if (log.isInfoEnabled())
            log.info("Render path: " + path);

        TemplateFactory.getTemplateFactory()
                .loadTemplate(path)
                .render(request, response, model);

//		WebEngine.setRequest(request);
//    	WebEngine.setResponse(response);
//    	
//    	Engine we = WebEngine.getEngine();
//    	try {
//			we.getTemplate(path).render(model, response.getOutputStream());
//		} catch (ParseException e) {
//			e.printStackTrace();
//		}

    }

}
