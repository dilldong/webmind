package org.mind.framework.http;


import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.http.HttpStatus;
import org.mind.framework.util.JsonUtils;
import org.mind.framework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;

@Getter
@NoArgsConstructor
public class Response<T> {

    @Expose
    private int code;

    @Expose
    private String msg;

    @Expose
    private boolean success;

    @Expose
    private T result;

    public Response(int code, String msg) {
        this.code = code;
        this.msg = msg;
        this.success = this.code == HttpStatus.SC_OK;
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
        return
                JsonUtils.toJson(
                        this,
                        new TypeToken<Response<T>>() {
                        }.getType(),
                        excludesFieldsWithoutExpose);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("code", code)
                .append("msg", msg)
                .append("success", success)
                .append("result", result)
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
        return toJson(excludesFieldsWithoutExpose, false, skipField);
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
        // 过滤json中的children字段
        GsonBuilder gsonBuilder = new GsonBuilder().setExclusionStrategies(new ExclusionStrategy() {
            Map<String, Field> fieldMap = ReflectionUtils.getDeclaredFieldByMap(Response.class);

            @Override
            public boolean shouldSkipField(FieldAttributes field) {
                if (fieldMap.containsKey(field.getName()))
                    return false;

                boolean isSkip = StringUtils.contains(Arrays.toString(fieldName), field.getName());

                return isShowField != isSkip;
            }

            @Override
            public boolean shouldSkipClass(Class<?> aClass) {
                return false;
            }
        }).setDateFormat("yyyy-MM-dd HH:mm:ss");

        if (excludesFieldsWithoutExpose)
            gsonBuilder.excludeFieldsWithoutExposeAnnotation();

        gsonBuilder.disableHtmlEscaping();// 禁止转义Unicode字符

        return JsonUtils.toJson(this,
                new TypeToken<Response<T>>() {
                }.getType(),
                gsonBuilder);
    }


}
