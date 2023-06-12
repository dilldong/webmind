package org.mind.framework;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.PropertyConfigurator;
import org.mind.framework.server.WebServer;
import org.mind.framework.server.WebServerConfig;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.util.IOUtils;
import org.mind.framework.util.JarFileUtils;
import org.springframework.context.ApplicationContext;

import java.io.InputStream;
import java.net.URL;
import java.util.Objects;

/**
 * @author Marcus
 * @version 1.0
 */
public class SpringApplication {
    private final String[] springLocations;
    private final String[] resources;
    private final Class<?> mainClass;

    private SpringApplication(Class<?> mainClass, String[] springLocations, String[] resources) {
        this.mainClass = mainClass;
        this.springLocations = springLocations;
        this.resources = resources;
    }

    /**
     * Load the Spring service and use Tomcat to run the web project.
     */
    public static void run(Class<?> mainClass, String... args) {
        run(mainClass, null, args);
    }

    /**
     * Load the Spring service and use Tomcat to run the web project.
     */
    public static void run(Class<?> mainClass, String[] springLocations, String... args) {
        run(mainClass, springLocations, null, args);
    }

    /**
     * Load the Spring service and use Tomcat to run the web project.
     */
    public static void run(Class<?> mainClass, String[] springLocations, String[] resources, String... args) {
        new SpringApplication(mainClass, springLocations, resources).run(args);
    }

    /**
     * Load the Spring service, non-web project.
     */
    public static ApplicationContext runApplication(Class<?> mainClass, String[] springLocations){
        return runApplication(mainClass, null, springLocations);
    }

    /**
     * Load the Spring service, non-web project.
     */
    public static ApplicationContext runApplication(Class<?> mainClass, String log4j, String[] springLocations) {
        if(StringUtils.isNotEmpty(log4j)) {
            URL url = ClassUtils.getResource(mainClass, log4j);

            // jar in jar
            InputStream log4jInputStream;
            if (Objects.isNull(url)) {
                log4jInputStream = JarFileUtils.getJarEntryStream(
                        WebServerConfig.JAR_IN_CLASSES + IOUtils.DIR_SEPARATOR + log4j);
            } else
                log4jInputStream = ClassUtils.getResourceAsStream(mainClass, log4j);

            // load log4j
            PropertyConfigurator.configure(log4jInputStream);
        }

        // load spring
        return ContextSupport.initContextByClassPathFile(springLocations);
    }

    private void run(String... args) {
        new WebServer()
                .addSpringFile(springLocations)
                .addResource(resources)
                .startup();
    }

}
