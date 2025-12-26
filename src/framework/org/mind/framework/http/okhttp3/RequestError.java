package org.mind.framework.http.okhttp3;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.mind.framework.service.Cloneable;

/**
 * @version 1.0
 * @author Marcus
 * @date 2021-08-24
 */
@Getter
@Setter
public class RequestError implements Cloneable<RequestError> {
    private static final RequestError INSTANCE = new RequestError();
    /**
     * Error code.
     */
    private Integer code;

    /**
     * Error message.
     */
    private String msg;

    private RequestError() {
    }

    public static RequestError newInstance(Integer code){
        return newInstance(code, StringUtils.EMPTY);
    }

    public static RequestError newInstance(Integer code, String message){
        RequestError requestError = INSTANCE.clone();
        requestError.setCode(code);
        requestError.setMsg(message);
        return requestError;
    }

    @Override
    public RequestError clone() {
        try {
            return (RequestError) super.clone();
        } catch (CloneNotSupportedException ignored) {
        }
        return new RequestError();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("code", code)
                .append("msg", msg)
                .toString();
    }
}
