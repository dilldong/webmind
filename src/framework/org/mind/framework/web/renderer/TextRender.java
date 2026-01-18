package org.mind.framework.web.renderer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.util.JsonUtils;
import org.mind.framework.util.ResponseUtils;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
        String charset = StringUtils.defaultIfEmpty(characterEncoding, StandardCharsets.UTF_8.name());

        StringBuilder sb = new StringBuilder(40)
                .append(StringUtils.isEmpty(contentType) ? (isJson ? MediaType.APPLICATION_JSON_VALUE : MediaType.TEXT_HTML_VALUE) : contentType)
                .append(";charset=")
                .append(charset);

        response.setContentType(sb.toString());
        ResponseUtils.write(response.getOutputStream(), text.getBytes(charset));
    }

}
