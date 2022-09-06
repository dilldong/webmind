package org.mind.framework.renderer;

/**
 * render JavaScript.
 *
 * @author dp
 */
public class JavaScriptRender extends TextRender {

    public JavaScriptRender() {
        setContentType(MIME_JAVASCRIPT);
    }

    public JavaScriptRender(String text) {
        super(text);
        setContentType(MIME_JAVASCRIPT);
    }

    public JavaScriptRender(String text, String characterEncoding) {
        super(text, characterEncoding);
        setContentType(MIME_JAVASCRIPT);
    }

}
