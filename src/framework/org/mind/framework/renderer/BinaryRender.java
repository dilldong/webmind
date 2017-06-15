package org.mind.framework.renderer;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mind.framework.util.ResponseUtils;

/**
 * To render the http response binary stream, usually used for rendering PDF,
 * IMAGE, or other binary type
 *
 * @author dp
 */
public class BinaryRender extends Render {

    private byte[] data;

    public BinaryRender() {

    }

    public BinaryRender(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @Override
    public void render(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType(
                contentType == null ? "application/octet-stream" : contentType);

        response.setContentLength(data.length);
        ResponseUtils.write(response.getOutputStream(), data);
    }

}
