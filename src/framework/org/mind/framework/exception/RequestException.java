package org.mind.framework.exception;

import lombok.Getter;
import org.mind.framework.http.okhttp3.RequestError;

import java.util.Objects;

/**
 * @version 1.0
 * @auther Marcus
 */
public class RequestException extends RuntimeException {

    @Getter
    private RequestError error;

    public RequestException(Throwable cause) {
        super(cause);
    }

    public RequestException(String message) {
        super(message);
    }

    public RequestException(RequestError error) {
        super(error.getMsg());
        this.error = error;
    }

    @Override
    public String toString() {
        if (Objects.nonNull(error))
            return error.toString();

        return super.toString();
    }
}
