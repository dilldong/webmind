package org.mind.framework.exception;

import org.mind.framework.http.okhttp3.RequestError;

/**
 * @version 1.0
 * @auther Marcus
 */
public class RequestException extends RuntimeException {

    private String errCode;
    private RequestError error;

    public RequestException(Throwable cause) {
        super(cause);
    }

    public RequestException(RequestError error) {
        super(error.getMsg());
        this.error = error;
        errCode = String.valueOf(error.getCode());
    }

    @Override
    public String toString() {
        return error.toString();
    }
}
