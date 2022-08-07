package org.mind.framework.exception;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2022/8/7
 */
public class ResourceNotFoundException extends NotSupportedException{
    public ResourceNotFoundException(String exceptionMessage) {
        super(exceptionMessage);
    }

    public ResourceNotFoundException(String exceptionMessage, Throwable t) {
        super(exceptionMessage, t);
    }

    public ResourceNotFoundException(Throwable t) {
        super(t);
    }
}
