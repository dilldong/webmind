package org.mind.framework.renderer;

import org.apache.commons.lang3.StringUtils;
import org.mind.framework.util.ResponseUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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
                StringUtils.isEmpty(contentType) ? MIME_OCTET_STREAM : contentType);

        response.setContentLength(data.length);
        ResponseUtils.write(response.getOutputStream(), data);
    }

}
