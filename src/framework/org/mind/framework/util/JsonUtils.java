package org.mind.framework.util;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.ToNumberStrategy;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class JsonUtils {
    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);

    public static final String EMPTY_JSON_OBJECT = "{}";

    public static final String EMPTY_JSON_ARRAY = "[]";

    public static final String BEGIN_BRACKETS = "{";

    public static final String END_BRACKETS = "}";

    public static final String BEGIN_ARRARYS = "[";

    public static final String END_ARRARYS = "]";

    // Delete spaces, carriage returns, newlines, and tabs in a string
    private static final Pattern REPLACE_PATT = Pattern.compile("\\s{2,}|\t|\r|\n");

    // JSON attribute
    private static final Pattern JSON_ATTR_PATTERN = Pattern.compile("['\":]*");

    // JSON attribute
    private static final Pattern JSON_OBJ_ATTR_PATTERN = Pattern.compile("^(['|\"]:)?");

    /**
     * Long type serialization maintains long type and will not be converted to double type
     */
    private static final ToNumberStrategy OBJECT_TO_NUMBER = (in) -> {
        String value = in.nextString();
        if (value.contains(IOUtils.DOT_SEPARATOR) || value.contains("e") || value.contains("E"))
            return Double.parseDouble(value);

        long aLong = Long.parseLong(value);
        return aLong <= Integer.MAX_VALUE ? (int) aLong : aLong;
    };

    private static class SingletonHolder {
        private SingletonHolder() {
        }

        private static final Gson GSON_INSTANCE =
                new GsonBuilder()
                        .setDateFormat(DateUtils.DATE_TIME_PATTERN)
                        .disableHtmlEscaping()
                        .setObjectToNumberStrategy(OBJECT_TO_NUMBER)
                        .create();
    }

    private static class SingletonExposedHolder {
        private SingletonExposedHolder() {
        }

        private static final Gson GSON_INSTANCE =
                new GsonBuilder()
                        .setDateFormat(DateUtils.DATE_TIME_PATTERN)
                        .disableHtmlEscaping()
                        .excludeFieldsWithoutExposeAnnotation()
                        .setObjectToNumberStrategy(OBJECT_TO_NUMBER)
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
        if (StringUtils.isBlank(text))
            return false;

        return text.startsWith(BEGIN_ARRARYS) && text.endsWith(END_ARRARYS);
    }

    public static boolean isJsonObject(String text) {
        if (StringUtils.isBlank(text))
            return false;

        return text.startsWith(BEGIN_BRACKETS) && text.endsWith(END_BRACKETS);
    }

    /**
     * 返回JSON中第一个name属性的值
     */
    public static String getAttribute(String searchName, String json) {
        if (StringUtils.isEmpty(searchName) || StringUtils.isEmpty(json))
            return StringUtils.EMPTY;

        int index = json.indexOf("\"" + searchName + "\":");
        if (index > -1) {
            String result = StringUtils.substringBetween(json, searchName, ",");
            if (StringUtils.isEmpty(result))
                result = StringUtils.substringBetween(json, searchName, END_BRACKETS);

            return JSON_ATTR_PATTERN.matcher(result).replaceAll(StringUtils.EMPTY).trim();
        }

        return StringUtils.EMPTY;
    }

    /**
     * 返回JSON对象中第一层级的name对象
     */
    public static String getAttributeObject(String searchName, String json) {
        if (StringUtils.isEmpty(searchName) || StringUtils.isEmpty(json))
            return StringUtils.EMPTY;

        int index = json.indexOf("\"" + searchName + "\":");
        if (index > -1) {
            JsonElement rootElement = JsonUtils.fromJson(json, JsonElement.class);
            JsonElement element = each(rootElement, searchName);
            if (Objects.isNull(element))
                return StringUtils.EMPTY;

            element = element.getAsJsonObject().get(searchName);

            if (Objects.nonNull(element) && !element.isJsonNull())
                return element.toString();
        }
        return StringUtils.EMPTY;
    }

    private static JsonElement each(JsonElement element, String searchName) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                if (entry.getKey().equals(searchName))
                    return obj;

                JsonElement value = entry.getValue();
                if (value.isJsonObject()) {
                    JsonObject result = (JsonObject) entry.getValue();
                    if (result.has(searchName))
                        return result;
                } else if (value.isJsonArray()) {
                    JsonArray result = (JsonArray) entry.getValue();
                    for (JsonElement jsonElement : result) {
                        JsonElement child = each(jsonElement, searchName);
                        if (Objects.nonNull(child))
                            return child;
                    }
                }
            }
        } else if (element.isJsonArray()) {
            JsonArray result = element.getAsJsonArray();
            for (JsonElement jsonElement : result) {
                JsonElement child = each(jsonElement, searchName);
                if (Objects.nonNull(child))
                    return child;
            }
        }

        return null;
    }

    public static String toJson(Object target) {
        return toJson(target, false);
    }

    public static String toJson(Object target, boolean excludesFieldsWithoutExpose) {
        return toJson(target, target.getClass(), excludesFieldsWithoutExpose);
    }

    public static String toJson(Object target, Type targetType) {
        return toJson(target, targetType, false);
    }

    public static String toJson(Object target, Type targetType, boolean excludesFieldsWithoutExpose) {
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

    public static String toJson(Object target, boolean isShowField, String... fieldName) {
        return toJson(target, false, isShowField, fieldName);
    }

    public static String toJson(Object target, boolean excludesFieldsWithoutExpose, boolean isShowField, String... fieldName) {
        return toJson(target, target.getClass(), excludesFieldsWithoutExpose, isShowField, fieldName);
    }

    public static String toJson(Object target, Type type, boolean excludesFieldsWithoutExpose, boolean isShowField, String... fieldName) {
        final String fieldNameString = StringUtils.substringBetween(Arrays.toString(fieldName), BEGIN_ARRARYS, END_ARRARYS);
        if (StringUtils.isEmpty(fieldNameString))
            return toJson(target, type, excludesFieldsWithoutExpose);


        // filter children field
        GsonBuilder gsonBuilder = (excludesFieldsWithoutExpose ? getExposedSingleton() : getSingleton())
                .newBuilder()
                .setExclusionStrategies(
                        new ExclusionStrategy() {
                            final Map<String, Field> fieldMap = ReflectionUtils.getDeclaredFieldByMap(Response.class);

                            @Override
                            public boolean shouldSkipField(FieldAttributes field) {
                                if (fieldMap.containsKey(field.getName()))
                                    return false;

                                boolean isSkip = StringUtils.contains(fieldNameString, field.getName());
                                return isShowField != isSkip;
                            }

                            @Override
                            public boolean shouldSkipClass(Class<?> aClass) {
                                return false;
                            }
                        });

        return gsonBuilder.create().toJson(target, type);
    }

    public static <V> V fromJson(String json, Class<V> clazz) {
        return fromJson(json, TypeToken.get(clazz));
    }

    public static <V> V fromJson(String json, TypeToken<V> typeToken) {
        return fromJson(json, typeToken, true);
    }

    public static <V> V fromJson(String json, TypeToken<V> typeToken, boolean printErrorStack) {
        if (StringUtils.isBlank(json))
            return null;

        try {
            return getSingleton().fromJson(json, typeToken);
        } catch (Exception ex) {
            if (printErrorStack)
                log.error(ex.getMessage(), ex);
            else
                log.error(ex.getMessage());
            return null;
        }
    }

    public static <V> V fromJson(Reader jsonReader, Class<V> clazz) {
        return fromJson(jsonReader, TypeToken.get(clazz));
    }

    public static <V> V fromJson(Reader jsonReader, TypeToken<V> typeToken) {
        return fromJson(jsonReader, typeToken, true);
    }

    public static <V> V fromJson(Reader jsonReader, TypeToken<V> typeToken, boolean printErrorStack) {
        try {
            return getSingleton().fromJson(jsonReader, typeToken);
        } catch (Exception ex) {
            if (printErrorStack)
                log.error(ex.getMessage(), ex);
            else
                log.error(ex.getMessage());
            return null;
        }
    }

    /**
     * Delete spaces, carriage returns, newlines, and tabs in a string
     *
     * @param source origin string
     * @return the deleted string
     */
    public static String deletionBlank(String source) {
        if (StringUtils.isEmpty(source))
            return StringUtils.EMPTY;

        return REPLACE_PATT.matcher(source).replaceAll(StringUtils.EMPTY);
    }

    private static String defaultEmpty(Object target) {
        Class<?> clazz = target.getClass();
        if (Collection.class.isAssignableFrom(clazz) || clazz.isArray())
            return EMPTY_JSON_ARRAY;

        return EMPTY_JSON_OBJECT;
    }
}

