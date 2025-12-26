package org.mind.framework.util;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @version 1.0
 * @author Marcus
 * @date 2022/8/7
 */
public class ClassUtils {
    private static final Map<String, Class<?>> CLAZZ_MAP = new HashMap<>(64);

    private ClassUtils() {
    }

    public static Class<?> getClass(String clazz) throws ClassNotFoundException {
        if(CLAZZ_MAP.containsKey(clazz))
            return CLAZZ_MAP.get(clazz);

        Class<?> c = null;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (Objects.nonNull(loader)) {
            try {
                c = Class.forName(clazz, true, loader);
            } catch (ClassNotFoundException ignored) {}
        }

        if(Objects.isNull(c))
            c = Class.forName(clazz);

        CLAZZ_MAP.put(clazz, c);
        return c;
    }

    public static Object newInstance(String clazz) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return getClass(clazz).newInstance();
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
