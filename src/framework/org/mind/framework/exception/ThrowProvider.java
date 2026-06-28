package org.mind.framework.exception;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletionException;

/**
 * @author dp
 */
public interface ThrowProvider {
    /**
     * Lamanda internally throws checked exception
     * The checked exception is hidden, to deceive the compiler.
     */
    static <V extends Throwable> void doThrow(Throwable e) throws V {
        throw (V) e;
    }

    static Throwable unwrapCause(Throwable t) {
        while (t instanceof CompletionException || t instanceof InvocationTargetException) {
            Throwable cause = t.getCause();
            if (cause == null)
                break;

            t = cause;
        }
        return t;
    }
}
