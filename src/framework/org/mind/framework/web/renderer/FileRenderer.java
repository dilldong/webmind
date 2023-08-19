package org.mind.framework.web.renderer;

import org.apache.commons.lang3.StringUtils;
import org.mind.framework.util.ResponseUtils;
import org.mind.framework.web.Action;
import org.mind.framework.web.dispatcher.handler.HandlerResult;
import org.springframework.http.MediaType;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
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
        if (Objects.isNull(file) || !file.exists()) {
            HandlerResult.setRequestAttribute(request);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        BasicFileAttributes basicFileAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        if (!basicFileAttributes.isRegularFile()) {
            HandlerResult.setRequestAttribute(request);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (basicFileAttributes.size() > Integer.MAX_VALUE) {
            HandlerResult.setRequestAttribute(request);
            throw new IOException(String.format("Resource content too long (beyond Integer.MAX_VALUE): %s", file.getName()));
        }

        String mime = contentType;
        if (StringUtils.isEmpty(mime)) {
            ServletContext context = Action.getActionContext().getServletContext();
            mime = context.getMimeType(file.getName());
            if (StringUtils.isEmpty(mime))
                mime = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        response.setContentType(mime);
        response.setContentLength((int) basicFileAttributes.size());

        ResponseUtils.write(response.getOutputStream(), this.file);
    }

}
