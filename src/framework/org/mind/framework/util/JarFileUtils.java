package org.mind.framework.util;

import org.apache.commons.io.IOUtils;
import org.mind.framework.exception.ThrowProvider;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.jar.JarFile;

/**
 * @author Marcus
 * @version 1.0
 * @date 2022-03-17
 */
public final class JarFileUtils {
    public static String getRuntimePath() {
        String classPath = String.join(".", JarFileUtils.class.getName().replaceAll("\\.", "/"), "class");
        URL resource = JarFileUtils.class.getClassLoader().getResource(classPath);
        if (resource == null)
            return null;

        String urlString = resource.toString();
        int insidePathIndex = urlString.indexOf('!');

        if (insidePathIndex > -1) {
            urlString = urlString.substring(urlString.indexOf("file:") + 5, insidePathIndex);
            return urlString;
        }

        return urlString.substring(urlString.indexOf("file:") + 5, urlString.length() - classPath.length());
    }

    public static String getJarEntry(String fileName) {
        return getJarEntry(getRuntimePath(), fileName);
    }

    public static String getJarEntry(String jarPath, String fileName) {
        InputStream in = getJarEntryStream(jarPath, fileName);
        try {
            return IOUtils.toString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ThrowProvider.doThrow(e);
            return null;
        } finally {
            IOUtils.closeQuietly(in);
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
