package org.mind.framework.server;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.mind.framework.util.JarFileUtils;
import org.mind.framework.util.PropertiesUtils;

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.Set;

/**
 * @author Marcus
 * @version 1.0
 * @date 2022-05-14
 */
@Getter
@Slf4j
public class WebServerConfig {
    public static final String JAR_IN_CLASSES = "BOOT-INF/classes";
    private static final String SERVER_PROPERTIES = "/server.properties";
    private static final String JAR_PROPERTIES = String.format("%s%s", JAR_IN_CLASSES, SERVER_PROPERTIES);

    private String contextPath = StringUtils.EMPTY;

    @Setter
    private String tomcatBaseDir;

    private String webXml;

    private String serverName = "Tomcat";

    private int port = 10030;

    private int connectionTimeout = 20_000;

    private int maxConnections = 1024;

    private int minSpareThreads = 5;

    private int maxThreads = 200;

    private int acceptCount = 100;

    private int sessionTimeout = 30;

    private String staticSuffix = "css|js|jpg|png|gif|ico|svg|html|htm|xls|xlsx|doc|docx|ppt|pptx|pdf|rar|zip|txt";

    private String resourceExpires = "-1";

    private final String containerAware = "Spring";

    private String templateEngine;

    @Setter
    private transient Set<String> springFileSet;

    @Setter
    private transient Set<String> resourceSet;

    private WebServerConfig() {
        InputStream in;
        URL url = WebServer.class.getResource(SERVER_PROPERTIES);

        try {
            if (url != null)
                in = WebServer.class.getResourceAsStream(SERVER_PROPERTIES);
            else
                in = JarFileUtils.getJarEntryStream(JAR_PROPERTIES);
        } catch (Exception e) {
            log.warn("Not found 'server.properties', Configure the {} server with defaults", serverName);
            return;
        }

        Properties properties = PropertiesUtils.getProperties(in);
        if (properties != null) {
            this.contextPath = properties.getProperty("server.contextPath", contextPath);
            this.tomcatBaseDir = properties.getProperty("server.baseDir", tomcatBaseDir);
            this.webXml = properties.getProperty("server.webXml", StringUtils.EMPTY);
            this.serverName = properties.getProperty("server", serverName);
            this.port = Integer.parseInt(properties.getProperty("server.port", String.valueOf(port)));
            this.connectionTimeout = Integer.parseInt(properties.getProperty("server.connectionTimeout", String.valueOf(connectionTimeout)));
            this.maxConnections = Integer.parseInt(properties.getProperty("server.maxConnections", String.valueOf(maxConnections)));
            this.maxThreads = Integer.parseInt(properties.getProperty("server.maxThreads", String.valueOf(maxThreads)));
            this.minSpareThreads = Integer.parseInt(properties.getProperty("server.minSpareThreads", String.valueOf(minSpareThreads)));
            this.acceptCount = Integer.parseInt(properties.getProperty("server.acceptCount", String.valueOf(acceptCount)));

            this.sessionTimeout = Integer.parseInt(properties.getProperty("server.sessionTimeout", String.valueOf(sessionTimeout)));
            this.staticSuffix = properties.getProperty("server.resourceValue", staticSuffix);
            this.resourceExpires = properties.getProperty("server.resourceExpires", resourceExpires);
            this.templateEngine = properties.getProperty("server.templateEngine", StringUtils.EMPTY);
        }
    }

    public static WebServerConfig init() {
        return new WebServerConfig();
    }
}
