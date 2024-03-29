package org.mind.framework.web.server;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.exception.WebServerException;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.util.JarFileUtils;
import org.mind.framework.util.PropertiesUtils;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
    public static final WebServerConfig INSTANCE = new WebServerConfig();
    public static final String JAR_IN_CLASSES = "BOOT-INF/classes";
    private static final String SERVER_PROPERTIES = "server.properties";
    private static final String JAR_PROPERTIES = String.format("%s/%s", JAR_IN_CLASSES, SERVER_PROPERTIES);

    private String contextPath = StringUtils.EMPTY;

    private String nioMode = "nio";

    @Setter
    private String tomcatBaseDir = StringUtils.EMPTY;

    private String resourceDir = StringUtils.EMPTY;

    private String resourceRootFiles = StringUtils.EMPTY;

    private String webXml = StringUtils.EMPTY;

    private String serverName = "Tomcat";

    private int port = 8080;

    // if non-setting, listen on all available network
    private String bindAddress;

    private boolean http2Enabled;

    // ms, Maximum wait time when a client connects to a Tomcat server
    private int connectionTimeout = 20_000;

    private int maxConnections = 1024;

    private int minSpareThreads = 5;

    private int maxThreads = 200;

    private int acceptCount = 100;

    private int sessionTimeout = 30;

    private String staticSuffix = "css|js|jpg|png|gif|jpeg|webp|ico|svg|html|htm|rtf|ttf|tof|woff|woff2|csv|xls|xlsx|doc|docx|ppt|pptx|pdf|rar|zip|txt|xml|mov|mp3|aac|avi|mpeg|swf";

    private String compression = "on";

    private int compressionMinSize = 2048;

    private String compressibleMimeType = "text/html,text/xml,text/plain,text/css,text/javascript,application/javascript,application/json,application/xml";

    private transient Map<String, String> mimeMapping;

    private String resourceExpires = "-1";

    private final String containerAware = "Spring";

    private String tldSkipPatterns = "*.jar";

    private String templateEngine = StringUtils.EMPTY;

    // OkHttpClient setting
    private int maxRequestsPerHost = 200;
    private int maxRequests = 200;
    private int connectTimeout = 15;// SECONDS
    private int readTimeout = 15;   // SECONDS
    private int writeTimeout = 15;  // SECONDS
    private int pingInterval = -1;  // SECONDS

    @Setter
    private transient Set<String> springFileSet;

    @Setter
    private transient Set<String> resourceSet;

    @Setter
    private transient Set<Class<?>> springConfigClassSet;

    private WebServerConfig() {
        InputStream in;
        URL url = ClassUtils.getResource(WebServer.class, SERVER_PROPERTIES);

        try {
            if (url != null)
                in = ClassUtils.getResourceAsStream(WebServer.class, SERVER_PROPERTIES);
            else
                in = JarFileUtils.getJarEntryStream(JAR_PROPERTIES);
        } catch (Exception e) {
            log.warn("Not found 'server.properties', Configure the {} server with defaults", serverName);
            return;
        }

        Properties properties = PropertiesUtils.getProperties(in);
        if (Objects.nonNull(properties)) {
            this.serverName = properties.getProperty("server", serverName);
            this.nioMode = properties.getProperty("server.nio.mode", contextPath);
            this.contextPath = properties.getProperty("server.contextPath", contextPath);
            this.tomcatBaseDir = properties.getProperty("server.baseDir", tomcatBaseDir);
            this.resourceDir = properties.getProperty("server.resourceDirectory", resourceDir);
            this.resourceRootFiles = properties.getProperty("server.resourceRootFiles", resourceRootFiles);
            this.webXml = properties.getProperty("server.webXml", webXml);
            this.port = Integer.parseInt(properties.getProperty("server.port", String.valueOf(port)));
            this.connectionTimeout = Integer.parseInt(properties.getProperty("server.connectionTimeout", String.valueOf(connectionTimeout)));
            this.maxConnections = Integer.parseInt(properties.getProperty("server.maxConnections", String.valueOf(maxConnections)));
            this.maxThreads = Integer.parseInt(properties.getProperty("server.maxThreads", String.valueOf(maxThreads)));
            this.minSpareThreads = Integer.parseInt(properties.getProperty("server.minThreads", String.valueOf(minSpareThreads)));
            this.acceptCount = Integer.parseInt(properties.getProperty("server.acceptCount", String.valueOf(acceptCount)));
            this.tldSkipPatterns = properties.getProperty("server.tldSkipPatterns", tldSkipPatterns);

            this.bindAddress = properties.getProperty("server.bind-address");
            this.http2Enabled = Boolean.parseBoolean(properties.getProperty("server.http2.enabled", "false"));

            this.compression = properties.getProperty("server.compression", compression);
            this.compressionMinSize = Integer.parseInt(properties.getProperty("server.compression.minSize", String.valueOf(compressionMinSize)));
            this.compressibleMimeType = properties.getProperty("server.compression.mimeType", compressibleMimeType);

            this.sessionTimeout = Integer.parseInt(properties.getProperty("server.sessionTimeout", String.valueOf(sessionTimeout)));
            this.staticSuffix = properties.getProperty("server.resourceSuffix", staticSuffix);
            this.resourceExpires = properties.getProperty("server.resourceExpires", resourceExpires);
            this.templateEngine = properties.getProperty("server.templateEngine", templateEngine);

            // OkHttpClient
            this.maxRequestsPerHost = Integer.parseInt(properties.getProperty("okhttp.maxRequestsPerHost", String.valueOf(maxRequestsPerHost)));
            this.maxRequests = Integer.parseInt(properties.getProperty("okhttp.maxRequests", String.valueOf(maxRequests)));
            this.connectTimeout = Integer.parseInt(properties.getProperty("okhttp.connectTimeout", String.valueOf(connectTimeout)));
            this.readTimeout = Integer.parseInt(properties.getProperty("okhttp.readTimeout", String.valueOf(readTimeout)));
            this.writeTimeout = Integer.parseInt(properties.getProperty("okhttp.writeTimeout", String.valueOf(writeTimeout)));
            this.pingInterval = Integer.parseInt(properties.getProperty("okhttp.pingInterval", String.valueOf(pingInterval)));
        }
    }

    public InetAddress getBindAddress(){
        if(StringUtils.isEmpty(this.bindAddress))
            return null;

        try {
            return InetAddress.getByName(this.bindAddress);
        } catch (UnknownHostException e) {
            throw new WebServerException(e.getMessage(), e);
        }
    }

    protected WebServerConfig initMimeMapping() {
        if(Objects.nonNull(mimeMapping) && !mimeMapping.isEmpty())
            return this;

        mimeMapping = Collections.unmodifiableMap(
                new HashMap<String, String>() {{
                    put("css", "text/css; charset=UTF-8");
                    put("js", "text/javascript; charset=UTF-8");

                    put("png", "image/png");
                    put("jpg", "image/jpeg");
                    put("jpeg", "image/jpeg");
                    put("webp", "image/webp");
                    put("svg", "image/svg+xml");
                    put("ico", "image/vnd.microsoft.icon");
                    put("gif", "image/gif");

                    put("mov", "video/quicktime");
                    put("mp3", "audio/mpeg");
                    put("aac", "audio/aac");
                    put("avi", "video/x-msvideo");
                    put("mpeg", "video/mpeg");
                    put("swf", "application/x-shockwave-flash");

                    put("html", "text/html; charset=UTF-8");
                    put("htm", "text/html; charset=UTF-8");

                    put("rtf", "application/rtf");
                    put("ttf", "font/ttf");
                    put("tof", "font/tof");
                    put("woff", "font/woff");
                    put("woff2", "font/woff2");

                    put("doc", "application/msword");
                    put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                    put("ppt", "application/vnd.ms-powerpoint");
                    put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
                    put("xls", "application/vnd.ms-excel");
                    put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

                    put("csv", "text/csv");
                    put("pdf", "application/pdf");
                    put("txt", "text/plain");
                    put("xml", "text/xml");

                    put("rar", "application/x-rar-compressed");
                    put("zip", "application/zip");
                }});
        return this;
    }
}
