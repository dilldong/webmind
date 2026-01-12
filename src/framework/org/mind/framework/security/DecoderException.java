package org.mind.framework.security;

/**
 * @version 1.0
 * @author Marcus
 * @date 2023/12/31
 */
public class DecoderException extends IllegalStateException {
    private final Throwable cause;

    DecoderException(String var1, Throwable var2) {
        super(var1);
        this.cause = var2;
    }

    @Override
    public Throwable getCause() {
        return this.cause;
    }
}
