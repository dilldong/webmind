package org.mind.framework.util;

import org.apache.commons.io.IOUtils;
import org.mind.framework.exception.ThrowProvider;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author Marcus
 * @version 1.0
 * @date 2022-03-17
 */
public final class JarFileUtils {
    private static final String CLASS_PATH =
            String.join(
                    org.mind.framework.util.IOUtils.DOT_SEPARATOR,
                    JarFileUtils.class.getName().replaceAll("\\.", org.mind.framework.util.IOUtils.DIR_SEPARATOR),
                    "class");

    public static String getRuntimePath() {
        URL url = ClassUtils.getResource(JarFileUtils.class, CLASS_PATH);
        if (Objects.isNull(url))
            return null;

        String urlString = url.toString();
        int insidePathIndex = urlString.indexOf('!');

        if (insidePathIndex > -1) {
            urlString = urlString.substring(urlString.indexOf("file:") + 5, insidePathIndex);
            return urlString;
        }

        return urlString.substring(urlString.indexOf("file:") + 5, urlString.length() - CLASS_PATH.length());
    }

    public static JarEntry getJarEntry(String fileName) {
        return getJarEntry(getRuntimePath(), fileName);
    }

    public static JarEntry getJarEntry(String jarPath, String fileName) {
        try (JarFile jarFile = new JarFile(jarPath)) {
            return jarFile.getJarEntry(fileName);
        } catch (IOException e) {
            ThrowProvider.doThrow(e);
            return null;
        }
    }

    public static String getJarEntryContent(String fileName) {
        return getJarEntryContent(getRuntimePath(), fileName);
    }

    public static String getJarEntryContent(String jarPath, String fileName) {
        try (InputStream in = getJarEntryStream(jarPath, fileName)) {
            return IOUtils.toString(Objects.requireNonNull(in), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ThrowProvider.doThrow(e);
            return null;
        }
    }

    public static InputStream getJarEntryStream(String fileName) {
        return getJarEntryStream(getRuntimePath(), fileName);
    }

    public static InputStream getJarEntryStream(String jarPath, String fileName) {
        Objects.requireNonNull(fileName);
        Objects.requireNonNull(jarPath);

        try {
            JarFile jf = new JarFile(jarPath);
            return jf.getInputStream(jf.getJarEntry(fileName));
        } catch (IOException e) {
            ThrowProvider.doThrow(e);
            return null;
        }
    }
}
