package org.mind.framework.renderer.template;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Template using JSP which forward to specific JSP page.
 * 
 * @author dp
 */
public class JspTemplate implements Template {
	
	private static final Log log = LogFactory.getLog(JspTemplate.class);

	private String path;

	public JspTemplate(String path) {
		this.path = path;
	}

	/**
	 * Execute the JSP with given model.
	 * @throws ServletException 
	 * @throws IOException 
	 */
	public void render(
			HttpServletRequest request,
			HttpServletResponse response, 
			Map<String, Object> model) throws IOException  {
		
		Set<String> keys = model.keySet();
		for (String key : keys) {
			request.setAttribute(key, model.get(key));
		}
		
		try {
			request.getRequestDispatcher(path).forward(request, response);
		} catch (ServletException e) {
			log.error(e.getMessage(), e);
		}
	}

}
