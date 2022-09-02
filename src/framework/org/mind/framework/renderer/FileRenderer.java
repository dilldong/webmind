package org.mind.framework.renderer;

import org.apache.commons.lang3.StringUtils;
import org.mind.framework.Action;
import org.mind.framework.util.ResponseUtils;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

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
        if (Objects.isNull(file) || !file.isFile()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (file.length() > Integer.MAX_VALUE)
            throw new IOException(String.format("Resource content too long (beyond Integer.MAX_VALUE): %s", file.getName()));

        String mime = contentType;
        if (StringUtils.isEmpty(mime)) {
            ServletContext context = Action.getActionContext().getServletContext();
            mime = context.getMimeType(file.getName());
            if (StringUtils.isEmpty(mime))
                mime = MIME_OCTET_STREAM;
        }

        response.setContentType(mime);
        response.setContentLength((int) file.length());

        ResponseUtils.write(response.getOutputStream(), this.file);
    }

}
