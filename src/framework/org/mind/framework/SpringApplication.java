package org.mind.framework;

import lombok.extern.slf4j.Slf4j;
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

/**
 * @author Marcus
 * @version 1.0
 */
@Slf4j
public class SpringApplication {
    private final Object lock = new Object();
    private final String[] springLocations;
    private final Class<?> mainClass;
    private String[] resources;
    private String log4j;

    private SpringApplication(Class<?> mainClass, String[] springLocations, String[] resources) {
        this.mainClass = mainClass;
        this.springLocations = springLocations;
        this.resources = resources;
    }

    private SpringApplication(Class<?> mainClass, String[] springLocations, String log4j) {
        this.mainClass = mainClass;
        this.springLocations = springLocations;
        this.log4j = log4j;
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
    public static SpringApplication runApplication(Class<?> mainClass, String[] springLocations) {
        return runApplication(mainClass, null, springLocations);
    }

    /**
     * Load the Spring service, non-web project.
     */
    public static SpringApplication runApplication(Class<?> mainClass, String log4j, String[] springLocations) {
        SpringApplication application = new SpringApplication(mainClass, springLocations, log4j);
        application.runApplication();
        return application;
    }

    public void waiting(Thread mainThread) {
        this.waiting(StringUtils.defaultIfEmpty(mainThread.getName(), "Main-Graceful"), mainThread);
    }

    public void waiting(final String nameTag, final Thread mainThread) {
        Runtime.getRuntime().addShutdownHook(ExecutorFactory.newThread(nameTag, true, () -> {
            try {
                mainThread.interrupt();
                // 当收到停止信号时，等待主线程的执行完成
                mainThread.join();
            } catch (InterruptedException | IllegalStateException ignored) {
            } finally {
                log.info("Shutdown '{}' server completed.", nameTag);
            }
        }));

        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
            }
        }
    }

    public void close() {
        synchronized (lock) {
            lock.notify();
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
            ContextSupport.initSpringByClassPathFile(springLocations);
        }
    }

}
