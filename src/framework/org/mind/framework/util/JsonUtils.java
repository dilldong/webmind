package org.mind.framework.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Objects;

public class JsonUtils {
    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);

    public static final String EMPTY_JSON_OBJECT = "{}";

    public static final String EMPTY_JSON_ARRAY = "[]";

    public static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static class SingletonHolder {
        private SingletonHolder() {
        }

        private static final Gson GSON_INSTANCE =
                new GsonBuilder()
                        .setDateFormat(DEFAULT_DATE_PATTERN)
                        .disableHtmlEscaping()
                        .create();
    }

    private static class SingletonExposedHolder {
        private SingletonExposedHolder() {
        }

        private static final Gson GSON_INSTANCE =
                new GsonBuilder()
                        .setDateFormat(DEFAULT_DATE_PATTERN)
                        .disableHtmlEscaping()
                        .excludeFieldsWithoutExposeAnnotation()
                        .create();
    }

    public static Gson getSingleton() {
        return SingletonHolder.GSON_INSTANCE;
    }

    public static Gson getExposedSingleton() {
        return SingletonExposedHolder.GSON_INSTANCE;
    }

    public static boolean isJson(String text) {
        return (isJsonArray(text) || isJsonObject(text));
    }

    public static boolean isJsonArray(String text) {
        if (StringUtils.isBlank(text)) return false;

        return text.startsWith("[") && text.endsWith("]");
    }

    public static boolean isJsonObject(String text) {
        if (StringUtils.isBlank(text)) return false;

        return text.startsWith("{") && text.endsWith("}");
    }

    public static String toJson(Object target) {
        return toJson(target, false);
    }

    public static String toJson(Object target, boolean excludesFieldsWithoutExpose) {
        return toJson(target, target.getClass(), excludesFieldsWithoutExpose);
    }

    public static String toJson(Object target, TypeToken typeToken) {
        return toJson(target, typeToken, false);
    }

    public static String toJson(Object target, TypeToken targetType, boolean excludesFieldsWithoutExpose) {
        if (Objects.isNull(target))
            return EMPTY_JSON_OBJECT;

        final Gson gson = excludesFieldsWithoutExpose ? getExposedSingleton() : getSingleton();

        try {
            return Objects.isNull(targetType) ?
                    gson.toJson(target) :
                    gson.toJson(target, targetType.getType());
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
        return defaultEmpty(target);
    }

    public static String toJson(Object target, Class<? extends Object> targetType) {
        return toJson(target, targetType, false);
    }

    public static String toJson(Object target, Class<? extends Object> targetType, boolean excludesFieldsWithoutExpose) {
        if (Objects.isNull(target))
            return EMPTY_JSON_OBJECT;

        final Gson gson = excludesFieldsWithoutExpose ? getExposedSingleton() : getSingleton();
        try {
            return Objects.isNull(targetType) ?
                    gson.toJson(target) :
                    gson.toJson(target, targetType);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
        return defaultEmpty(target);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return fromJson(json, TypeToken.get(clazz));
    }

    public static <T> T fromJson(String json, TypeToken<T> typeToken) {
        if (StringUtils.isBlank(json))
            return null;

        try {
            return getSingleton().fromJson(json, typeToken.getType());
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return null;
        }
    }

    private static String defaultEmpty(Object target) {
        if (target.getClass().isAssignableFrom(Collection.class) || target.getClass().isArray())
            return EMPTY_JSON_ARRAY;

        return EMPTY_JSON_OBJECT;
    }
}

