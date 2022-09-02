package org.mind.framework.renderer;

import org.apache.commons.lang3.StringUtils;
import org.mind.framework.util.JsonUtils;
import org.mind.framework.util.ResponseUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Render html, javascript, css or any text type
 *
 * @author dp
 */
public class TextRender extends Render {

    private String characterEncoding;
    private String text;

    public TextRender() {
    }

    public TextRender(String text) {
        this.text = text;
    }

    public TextRender(String text, String characterEncoding) {
        this.text = text;
        this.characterEncoding = characterEncoding;
    }

    public String getCharacterEncoding() {
        return characterEncoding;
    }

    public void setCharacterEncoding(String characterEncoding) {
        this.characterEncoding = characterEncoding;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public void render(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // json
        boolean isJson = JsonUtils.isJson(text);
        String charset = StringUtils.isEmpty(characterEncoding) ? "UTF-8" : characterEncoding;

        StringBuilder sb = new StringBuilder(64)
                .append(StringUtils.isEmpty(contentType) ? (isJson ? MIME_JSON : MIME_TEXT_HTML) : contentType)
                .append(";charset=")
                .append(charset);

        response.setContentType(sb.toString());
        ResponseUtils.write(response.getOutputStream(), text.getBytes(charset));
    }

}
