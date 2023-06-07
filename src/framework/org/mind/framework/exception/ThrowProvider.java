package org.mind.framework.exception;

public interface ThrowProvider {
    /**
     * Lamanda internally throws checked exception
     * The checked exception is hidden, to deceive the compiler.
     */
    static <V extends Throwable> void doThrow(Throwable e) throws V {
        throw (V) e;
    }
}
