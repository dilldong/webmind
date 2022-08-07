package org.mind.framework.util;

import java.io.InputStream;
import java.net.URL;

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
        if (loader != null) {
            try {
                return Class.forName(clazz, true, loader);
            } catch (ClassNotFoundException e) {
            }
        }

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
        if (classLoader == null)
            return classObj.getClassLoader().getResourceAsStream(name);

        InputStream result = classLoader.getResourceAsStream(name);
        if (result == null) {
            classLoader = classObj.getClassLoader();
            if (classLoader != null)
                return classLoader.getResourceAsStream(name);
        }

        return result;
    }

    public static URL getResource(Class<?> classObj, String name) {
        while (name.startsWith("/"))
            name = name.substring(1);

        // current thread ClassLoader
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null)
            return classObj.getClassLoader().getResource(name);

        URL url = classLoader.getResource(name);
        if (url == null) {
            classLoader = classObj.getClassLoader();
            if (classLoader != null)
                return classLoader.getResource(name);
        }

        return url;
    }
}
