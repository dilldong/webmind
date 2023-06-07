package org.mind.framework;

import org.mind.framework.server.WebServer;

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

    public static void run(Class<?> mainClass, String... args) {
        run(mainClass, null, args);
    }

    public static void run(Class<?> mainClass, String[] springLocations, String... args) {
        run(mainClass, springLocations, null, args);
    }

    public static void run(Class<?> mainClass, String[] springLocations, String[] resources, String... args) {
        new SpringApplication(mainClass, springLocations, resources).run(args);
    }

    private void run(String... args) {
        new WebServer()
                .addSpringFile(springLocations)
                .addResource(resources)
                .startup();
    }

}
