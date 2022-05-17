package org.mind.framework.exception;

/**
 * @author Marcus
 * @version 1.0
 */
public class WebServerException extends RuntimeException{

    public WebServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
