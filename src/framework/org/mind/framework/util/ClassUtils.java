package org.mind.framework.util;

import java.io.InputStream;
import java.net.URL;
import java.util.Objects;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2022/8/7
 */
public class ClassUtils {
    private ClassUtils() {
    }

    public static Class<?> getClass(String clazz) throws ClassNotFoundException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (Objects.nonNull(loader))
            return Class.forName(clazz, true, loader);

        return Class.forName(clazz);
    }

    public static Object newInstance(String clazz) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return getClass(clazz).newInstance();
    }

    public static InputStream getResourceAsStream(Class<?> classObj, String name) {
        while (name.startsWith("/"))
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
        while (name.startsWith("/"))
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
