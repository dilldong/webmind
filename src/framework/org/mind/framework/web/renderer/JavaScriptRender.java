package org.mind.framework.web.renderer;

import org.mind.framework.util.HttpUtils;

/**
 * render JavaScript.
 *
 * @author dp
 */
public class JavaScriptRender extends TextRender {

    public JavaScriptRender() {
        setContentType(HttpUtils.MIME_JAVASCRIPT);
    }

    public JavaScriptRender(String text) {
        super(text);
        setContentType(HttpUtils.MIME_JAVASCRIPT);
    }

    public JavaScriptRender(String text, String characterEncoding) {
        super(text, characterEncoding);
        setContentType(HttpUtils.MIME_JAVASCRIPT);
    }

}
