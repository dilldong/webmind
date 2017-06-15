package org.mind.framework.exception;

/**
 * @author dongping
 */
public class NotSupportedException extends RuntimeException {

    private static final long serialVersionUID = -3149250570670823885L;

    /**
     * @author dongping
     */
    public NotSupportedException() {
        super();
    }

    /**
     * @param message
     * @param cause
     * @author dongping
     */
    public NotSupportedException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     * @author dongping
     */
    public NotSupportedException(String message) {
        super(message);
    }

    /**
     * @param cause
     * @author dongping
     */
    public NotSupportedException(Throwable cause) {
        super(cause);
    }


}
