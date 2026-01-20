package org.mind.framework.util;

import lombok.extern.slf4j.Slf4j;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.web.container.spring.DefaultByteArrayResource;
import org.mind.framework.web.server.WebServerConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;

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
@Slf4j
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
            return org.apache.commons.io.IOUtils.toString(Objects.requireNonNull(in), StandardCharsets.UTF_8);
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

    // Load from custom JAR path
    public static Resource loadResourceFromJar(String path) {
        String tempPath = path;

        if(tempPath.startsWith(ResourceLoader.CLASSPATH_URL_PREFIX))
            tempPath = tempPath.substring(ResourceLoader.CLASSPATH_URL_PREFIX.length());
        else if(tempPath.startsWith(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX))
            tempPath = tempPath.substring(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX.length());

        if(tempPath.startsWith(IOUtils.DIR_SEPARATOR))
            tempPath =  tempPath.substring(1);

        InputStream in = getJarEntryStream(
                WebServerConfig.JAR_IN_CLASSES + IOUtils.DIR_SEPARATOR + tempPath);

        if (Objects.nonNull(in)) {
            try {
                return new DefaultByteArrayResource(in, tempPath);
            } catch (IOException e) {
                log.error("Load resource form jar error, {}", e.getMessage());
            } finally {
                org.apache.commons.io.IOUtils.closeQuietly(in);
            }
        }

        return null;
    }
}
