package org.mind.framework.web.renderer;

public enum RenderType {
    REDIRECT("redirect:", 9),
    FORWARD("forward:", 8),
    SCRIPT("script:", 7);

    public final String keyName;
    public final int keyLength;

    RenderType(String keyName, int length) {
        this.keyName = keyName;
        this.keyLength = length;
    }
}
