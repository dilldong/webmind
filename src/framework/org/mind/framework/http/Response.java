package org.mind.framework.http;


import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang.StringUtils;
import org.mind.framework.util.JsonUtils;

import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;

import java.util.Arrays;

public class Response<T> {

    @Expose
    private int state;

    @Expose
    private String msg;

    @Expose
    private T body;

    public Response() {

    }

    public Response(int state, String msg) {
        this.state = state;
        this.msg = msg;
    }

    public Response(int state, String msg, T body) {
        this(state, msg);
        this.body = body;
    }

    public T getBody() {
        return body;
    }

    public void setBody(T body) {
        this.body = body;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
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
        return
                new StringBuilder()
                        .append(this.getState())
                        .append(":")
                        .append(this.getMsg())
                        .toString();
    }


    /**
     * 设置需要跳过的字段
     * @param excludesFieldsWithoutExpose
     * @param skipField 跳过的字段名称，不会在json中显示
     * @return
     */
    public String toJson(boolean excludesFieldsWithoutExpose, final String... skipField) {
        // 过滤json中的children字段
        GsonBuilder gsonBuilder = new GsonBuilder().setExclusionStrategies(new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes field) {
                return StringUtils.contains(Arrays.toString(skipField), field.getName());
            }

            @Override
            public boolean shouldSkipClass(Class<?> aClass) {
                return false;
            }
        }).setDateFormat("yyyy-MM-dd HH:mm:ss");

        if (excludesFieldsWithoutExpose)
            gsonBuilder.excludeFieldsWithoutExposeAnnotation();

        return JsonUtils.toJson(this,
                new TypeToken<Response<T>>() {
                }.getType(),
                gsonBuilder);
    }

}
