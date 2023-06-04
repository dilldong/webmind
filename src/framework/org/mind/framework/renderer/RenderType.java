package org.mind.framework.renderer;

public enum RenderType {
    REDIRECT("redirect:", 9),
    FORWARD("forward:", 8),
    SCRIPT("script:", 7);

    public String keyName;
    public int keyLength;

    RenderType(String keyName, int length) {
        this.keyName = keyName;
        this.keyLength = length;
    }
}
