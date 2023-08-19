package org.mind.framework.web.renderer.template;

import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.context.Context;
import org.apache.velocity.io.VelocityWriter;
import org.springframework.http.MediaType;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Velocity Template.
 *
 * @author dp
 */
public class VelocityTemplate implements Template {

    private final org.apache.velocity.Template template;
    private String contentType;
    private String encoding;

    public VelocityTemplate(org.apache.velocity.Template template) {
        this.template = template;
    }

    public VelocityTemplate(org.apache.velocity.Template template, String contentType, String encoding) {
        this(template);
        this.contentType = contentType;
        this.encoding = encoding;
    }

    @Override
    public void render(
            HttpServletRequest request,
            HttpServletResponse response,
            Map<String, Object> model) throws IOException {

        String charset = StringUtils.isEmpty(encoding) ? StandardCharsets.UTF_8.name() : encoding;
        StringBuilder sb = new StringBuilder(40);
        sb.append(StringUtils.isEmpty(contentType) ? MediaType.TEXT_HTML_VALUE : contentType)
                .append(";charset=")
                .append(charset);

        response.setContentType(sb.toString());
        response.setCharacterEncoding(charset);

        HttpSession session = request.getSession();
        ServletContext servletContext = session.getServletContext();

        model.put("sessionScope", session);
        model.put("contextPath", servletContext.getContextPath());
        model.put("applicationScope", servletContext);

        // init context:
        Context context = new VelocityContext(model);
        this.afterContextPrepared(context);

        // render:
        VelocityWriter vw = new VelocityWriter(response.getWriter());
        try {
            template.merge(context, vw);
            vw.flush();
        } finally {
            vw.recycle(null);
        }
    }

    /**
     * Let subclass do some initial work after Velocity context prepared.
     *
     * @param context Velocity context object.
     */
    protected void afterContextPrepared(Context context) {
    }
}
