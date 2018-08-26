package org.mind.framework.renderer.template;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.context.Context;
import org.apache.velocity.io.VelocityWriter;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;

/**
 * Velocity Template.
 * 
 * @author dp
 */
public class VelocityTemplate implements Template {

	private org.apache.velocity.Template template;
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
		
		StringBuilder sb = new StringBuilder(64);
		sb.append(contentType == null ? "text/html" : contentType)
				.append(";charset=")
				.append(encoding == null ? "UTF-8" : encoding);
		
		response.setContentType(sb.toString());
		response.setCharacterEncoding(encoding == null ? "UTF-8" : encoding);
		
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
	 * @param context
	 *            Velocity context object.
	 */
	protected void afterContextPrepared(Context context) {
	}
}
