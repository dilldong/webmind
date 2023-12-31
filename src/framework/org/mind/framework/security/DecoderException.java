package org.mind.framework.security;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2023/12/31
 */
public class DecoderException extends IllegalStateException {
    private final Throwable cause;

    DecoderException(String var1, Throwable var2) {
        super(var1);
        this.cause = var2;
    }

    public Throwable getCause() {
        return this.cause;
    }
}
