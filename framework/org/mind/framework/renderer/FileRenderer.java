package org.mind.framework.renderer;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mind.framework.Action;
import org.mind.framework.util.ResponseUtils;

/**
 * Render http response as binary stream. This is usually used to render PDF,
 * image, or any binary type.
 * 
 * @author dp
 */
public class FileRenderer extends Render {

	private File file;

	public FileRenderer() {
	}

	public FileRenderer(File file) {
		this.file = file;
	}

	public FileRenderer(String path) {
		this.file = new File(path);
	}

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	@Override
	public void render(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (file == null || !file.isFile()) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		if(file.length() > Integer.MAX_VALUE)
			throw new IOException("Resource content too long (beyond Integer.MAX_VALUE): " + file.getName());
			
		
		String mime = contentType;
		if (mime == null) {
			ServletContext context = Action.getActionContext().getServletContext();
			mime = context.getMimeType(file.getName());
			if (mime == null)
				mime = "application/octet-stream";
		}
		
		response.setContentType(mime);
		response.setContentLength((int) file.length());
		
		ResponseUtils.write(response.getOutputStream(), this.file);
	}

}
