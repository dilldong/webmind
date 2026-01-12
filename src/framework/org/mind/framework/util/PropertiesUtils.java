package org.mind.framework.util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import java.util.Properties;

/**
 * Read the .properties file.
 *
 * @author dp
 */
public class PropertiesUtils {

    private static final Logger logger = LoggerFactory.getLogger(PropertiesUtils.class);

    private static final String DEFAULT_PROPERTIES = "frame.properties";
    private static final String JAR_PROPERTIES = "BOOT-INF/classes/frame.properties";

    /**
     * The default parameter is frame.properties
     *
     * @return Properties
     */
    public static Properties getProperties() {
        InputStream in;
        URL url = ClassUtils.getResource(PropertiesUtils.class, DEFAULT_PROPERTIES);

        // jar in jar
        if (Objects.isNull(url))
            in = JarFileUtils.getJarEntryStream(JAR_PROPERTIES);
        else
            in = ClassUtils.getResourceAsStream(PropertiesUtils.class, DEFAULT_PROPERTIES);

        return getProperties(in);
    }

    /**
     * Specify the absolute path of the properties file
     *
     * @param resPath properties file absolute path
     * @return Properties
     * @throws IOException
     */
    public static Properties getProperties(String resPath) {
        try {
            return getProperties(new BufferedInputStream(new FileInputStream(resPath)));
        } catch (FileNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Specify properties input stream
     *
     * @param in BufferedInputStream or InputStream
     * @return Properties
     */
    public static Properties getProperties(InputStream in) {
        Properties props = new Properties();
        try {
            props.load(in);
            return props;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }finally {
            IOUtils.closeQuietly(in);
        }
        return null;
    }


    public static long getLong(Properties property, String key) {
        String str = getString(property, key);
        if (StringUtils.isEmpty(str))
            throw new IllegalArgumentException(String.format("Corresponding to the key value is empty or characters, For input string: \" %s \"", str));

        return Long.parseLong(str);
    }

    public static int getInteger(Properties property, String key) {
        String str = getString(property, key);
        if (StringUtils.isEmpty(str))
            throw new IllegalArgumentException(String.format("Corresponding to the key value is empty or characters, For input string: \" %s \"", str));

        return Integer.parseInt(str);
    }

    public static String getString(Properties property, String key) {
        if (property == null) {
            logger.error("Get Properties object is null, Please call the method: getProperties()");
            return null;
        }

        String str = property.getProperty(key);
        logger.debug("{} = {}", key, str);
        return str;
    }

}
