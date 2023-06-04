package org.mind.framework.http;


import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.mind.framework.Action;
import org.mind.framework.util.JsonUtils;

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
     * @return
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
     *
     * @return
     */
    public String toJson() {
        return this.toJson(false);
    }

    /**
     * 是否排除未标注 @Expose 注解的字段。false:不排出，true:排除
     *
     * @param excludesFieldsWithoutExpose
     * @return
     */
    public String toJson(boolean excludesFieldsWithoutExpose) {
        if (StringUtils.isEmpty(status))
            this.status = isSuccessful() ? SUCCESS : FAILED;

        return
                JsonUtils.toJson(
                        this,
                        new TypeToken<Response<T>>(){}.getType(),
                        excludesFieldsWithoutExpose);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("code", code)
                .append(" msg", msg)
                .append(" status", status)
                .append(" result", result)
                .toString();
    }

    /**
     * 设置需要跳过的字段
     *
     * @param excludesFieldsWithoutExpose
     * @param skipField                   跳过的字段名称，不会在json中显示
     * @return
     */
    public String toJson(boolean excludesFieldsWithoutExpose, final String... skipField) {
        if (Objects.nonNull(skipField) && skipField.length > 0)
            return toJson(excludesFieldsWithoutExpose, false, skipField);

        return toJson(excludesFieldsWithoutExpose);
    }


    /**
     * 设置需要跳过的字段
     *
     * @param excludesFieldsWithoutExpose
     * @param isShowField                 显示/隐藏
     * @param fieldName                   字段名称，根据isShow是否在json中显示
     * @return
     */
    public String toJson(boolean excludesFieldsWithoutExpose, final boolean isShowField, final String... fieldName) {
        if (StringUtils.isEmpty(status))
            this.status = isSuccessful() ? SUCCESS : FAILED;

        return JsonUtils.toJson(this,
                new TypeToken<Response<T>>(){}.getType(),
                excludesFieldsWithoutExpose,
                isShowField,
                fieldName);
    }


}
