package org.mind.framework.http;


import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.mind.framework.util.JsonUtils;
import org.mind.framework.web.Action;

import javax.servlet.http.HttpServletResponse;
import java.util.Objects;

@Getter
@NoArgsConstructor
public class Response<T> {
    public static final String SUCCESS = "success";
    public static final String FAILED = "failed";

    @Expose
    private int code;

    @Expose
    private String msg;

    @Expose
    private String status;

    @Expose
    private T result;

    public Response(int code, String msg) {
        this.code = code;
        this.msg = msg;
        this.status = isSuccessful() ? SUCCESS : FAILED;
    }

    public Response(int code, String msg, T result) {
        this(code, msg);
        this.result = result;
    }

    public Response<T> setResult(T result) {
        this.result = result;
        return this;
    }

    public Response<T> setCode(int code) {
        this.code = code;
        return this;
    }

    public Response<T> setMsg(String msg) {
        this.msg = msg;
        return this;
    }

    /**
     * Set response-code follow code
     */
    public Response<T> followHttpStatus(){
        Action.getActionContext().getResponse().setStatus(code);
        return this;
    }

    public boolean isSuccessful() {
        return this.code == HttpServletResponse.SC_OK;
    }

    /**
     * 默认不排除未标注 @Expose 注解的字段.
     */
    public String toJson() {
        return this.toJson(false);
    }

    /**
     * 是否只显示带@Expose注解的字段
     *
     * @param ofExpose true:只显示带@Expose的字段, false:全部显示
     */
    public String toJson(boolean ofExpose) {
        if (StringUtils.isEmpty(status))
            this.status = isSuccessful() ? SUCCESS : FAILED;

        return
                JsonUtils.toJson(
                        this,
                        new TypeToken<Response<T>>(){}.getType(),
                        ofExpose);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("code", code)
                .append("msg", msg)
                .append("status", status)
                .append("result", result)
                .toString();
    }

    public String toSimpleString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("code", code)
                .append("msg", msg)
                .append("status", status)
                .toString();
    }

    /**
     * 设置需要跳过的字段
     *
     * @param ofExpose  true:只显示带@Expose的字段, false:全部显示
     * @param skipField 跳过的字段名称，不会在结果中显示
     */
    public String toJson(boolean ofExpose, final String... skipField) {
        if (Objects.nonNull(skipField) && skipField.length > 0)
            return toJson(ofExpose, false, skipField);

        return toJson(ofExpose);
    }


    /**
     * 设置需要跳过的字段
     *
     * @param ofExpose      true:只显示带@Expose的字段, false:全部显示
     * @param isShowField   显示/隐藏
     * @param fieldName     字段名称，根据isShowField是否显示该字段
     */
    public String toJson(boolean ofExpose, final boolean isShowField, final String... fieldName) {
        if (StringUtils.isEmpty(status))
            this.status = isSuccessful() ? SUCCESS : FAILED;

        return JsonUtils.toJson(this,
                new TypeToken<Response<T>>(){}.getType(),
                ofExpose,
                isShowField,
                fieldName);
    }


}
