package org.mind.framework.exception;

/**
 * @author dp
 */
public class NotSupportedException extends RuntimeException {

    private static final long serialVersionUID = -3149250570670823885L;

    /**
     * @author dp
     */
    public NotSupportedException() {
        super();
    }

    /**
     * @param message
     * @param cause
     * @author dp
     */
    public NotSupportedException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     * @author dp
     */
    public NotSupportedException(String message) {
        super(message);
    }

    /**
     * @param cause
     * @author dp
     */
    public NotSupportedException(Throwable cause) {
        super(cause);
    }


}
