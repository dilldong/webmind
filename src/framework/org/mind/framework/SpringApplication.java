package org.mind.framework;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.PropertyConfigurator;
import org.mind.framework.server.WebServer;
import org.mind.framework.server.WebServerConfig;
import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.util.IOUtils;
import org.mind.framework.util.JarFileUtils;

import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author Marcus
 * @version 1.0
 */
@Slf4j
public class SpringApplication {
    private final Object lock = new Object();
    private final Class<?> mainClass;
    private String[] springLocations;
    private String[] resources;
    private String log4j;

    private SpringApplication(Class<?> mainClass, String[] springLocations, String[] resources) {
        this.mainClass = mainClass;
        this.springLocations = springLocations;
        this.resources = resources;
    }

    public SpringApplication(Class<?> mainClass) {
        this.mainClass = mainClass;
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
    public static SpringApplication runApplication(Class<?> mainClass) {
        return runApplication(mainClass, null);
    }

    /**
     * Load the Spring service, non-web project.
     */
    public static SpringApplication runApplication(Class<?> mainClass, String log4j) {
        return runApplication(mainClass, log4j, null);
    }

    /**
     * Load the Spring service, non-web project.
     */
    public static SpringApplication runApplication(Class<?> mainClass, String log4j, String[] springLocations) {
        SpringApplication application = new SpringApplication(mainClass);
        application.springLocations = springLocations;
        application.log4j = log4j;
        application.runApplication();
        return application;
    }

    public void waiting(long await, TimeUnit timeUnit) {
        this.waiting(await, timeUnit, Thread.currentThread());
    }

    public void waiting() {
        this.waiting(0, TimeUnit.MILLISECONDS, Thread.currentThread());
    }

    public void waiting(long await, TimeUnit timeUnit, Thread mainThread) {
        Runtime.getRuntime().addShutdownHook(ExecutorFactory.newDaemonThread("Main-Graceful", () -> {
            try {
                if (await > 0L)
                    timeUnit.sleep(await);

                mainThread.interrupt();
                // When received a stop signal,
                // wait for the execution of the main thread to complete.
                mainThread.join();
            } catch (InterruptedException | IllegalStateException ignored) {
            } finally {
                this.close();
                log.info("Shutdown '{}' server completed.", Thread.currentThread().getName());
            }
        }));

        this.await();
    }

    public void close() {
        synchronized (lock) {
            try {
                lock.notify();
            } catch (IllegalMonitorStateException ignored) {
            }
        }
    }

    private void await() {
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException | IllegalMonitorStateException ignored) {
            }
        }
    }

    // web project
    private void run(String... args) {
        new WebServer()
                .addSpringFile(springLocations)
                .addResource(resources)
                .startup();
    }

    // non-web project
    private void runApplication() {
        synchronized (lock) {
            if (StringUtils.isNotEmpty(log4j)) {
                URL url = ClassUtils.getResource(mainClass, log4j);

                // jar in jar
                InputStream log4jInputStream;
                if (Objects.isNull(url)) {
                    log4jInputStream = JarFileUtils.getJarEntryStream(
                            WebServerConfig.JAR_IN_CLASSES + IOUtils.DIR_SEPARATOR + log4j);
                } else
                    log4jInputStream = ClassUtils.getResourceAsStream(mainClass, log4j);

                // load log4j
                try {
                    PropertyConfigurator.configure(log4jInputStream);
                } finally {
                    org.apache.commons.io.IOUtils.closeQuietly(log4jInputStream);
                }
            }

            // load spring
            if (ArrayUtils.isEmpty(springLocations))
                ContextSupport.initSpringByAnnotationClass(this.mainClass);
            else
                ContextSupport.initSpringByClassPathFile(springLocations);
        }
    }

}
