package org.mind.framework.util;

import org.apache.commons.lang3.ArrayUtils;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Marcus
 * @version 1.0
 * @date 2022/8/7
 */
public class ClassUtils {
    private static final Map<String, Class<?>> CLAZZ_MAP = new HashMap<>(64);

    private ClassUtils() {
    }

    public static Field[] getDeclaredFields(Class<?> clazz) {
        if (clazz == null)
            throw new IllegalArgumentException("Class must not be null");

        return clazz.getDeclaredFields();
    }

    public static Map<String, Field> getDeclaredFieldByMap(Class<?> clazz) {
        Field[] fields = getDeclaredFields(clazz);
        if (ArrayUtils.isEmpty(fields))
            return Collections.emptyMap();

        return Arrays.stream(fields)
                .collect(Collectors.toMap(
                        Field::getName,
                        v -> v,
                        (v1, v2) -> v2
                ));
    }


    public static Class<?> getClass(String clazz) throws ClassNotFoundException {
        return CLAZZ_MAP.computeIfAbsent(clazz, c -> {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader != null) {
                try {
                    return Class.forName(clazz, true, loader);
                } catch (ClassNotFoundException ignored) {}
            }

            try {
                return Class.forName(clazz);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static Object newInstance(String clazz) throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        return getClass(clazz).getDeclaredConstructor().newInstance();
    }

    public static InputStream getResourceAsStream(Class<?> classObj, String name) {
        while (name.startsWith(IOUtils.DIR_SEPARATOR))
            name = name.substring(1);

        // current thread ClassLoader
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (Objects.isNull(classLoader))
            return classObj.getClassLoader().getResourceAsStream(name);

        InputStream result = classLoader.getResourceAsStream(name);
        if (Objects.isNull(result)) {
            classLoader = classObj.getClassLoader();
            if (Objects.nonNull(classLoader))
                result = classLoader.getResourceAsStream(name);
        }

        return result;
    }

    public static URL getResource(Class<?> classObj, String name) {
        while (name.startsWith(IOUtils.DIR_SEPARATOR))
            name = name.substring(1);

        // current thread ClassLoader
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (Objects.isNull(classLoader))
            return classObj.getClassLoader().getResource(name);

        URL url = classLoader.getResource(name);
        if (Objects.isNull(url)) {
            classLoader = classObj.getClassLoader();
            if (Objects.nonNull(classLoader))
                url = classLoader.getResource(name);
        }

        return url;
    }
}
