package org.mind.framework;

import lombok.SneakyThrows;
import org.mind.framework.server.WebServer;

/**
 * @author Marcus
 * @version 1.0
 */
public class SpringApplication {
    private String[] springLocations;
    private String[] resources;
    private Class<?> mainClass;

    private SpringApplication(Class<?> mainClass, String[] springLocations, String[] resources) {
        this.mainClass = mainClass;
        this.springLocations = springLocations;
        this.resources = resources;
    }

    public static void run(Class<?> mainClass, String[] springLocations, String[] resources, String... args) {
        new SpringApplication(mainClass, springLocations, resources).run(args);
    }

    @SneakyThrows
    private void run(String... args) {
        new WebServer()
                .addSpringFile(springLocations)
                .addResource(resources)
                .startServer();
    }

}
