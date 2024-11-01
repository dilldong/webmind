package org.mind.framework;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.util.IOUtils;
import org.mind.framework.util.JarFileUtils;
import org.mind.framework.web.server.WebServer;
import org.mind.framework.web.server.WebServerConfig;
import org.springframework.context.support.AbstractApplicationContext;

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
    private Class<?> configClass;
    private String[] springLocations;
    private String[] resources;
    private String log4j;

    private SpringApplication(){}

    private SpringApplication(Class<?> configClass){
        this.configClass = configClass;
    }

    private SpringApplication(String[] springLocations, String[] resources) {
        this.springLocations = springLocations;
        this.resources = resources;
    }

    /**
     * Load the Spring service and use Tomcat to run the web project.
     */
    public static void run(Class<?> configClass, String... args) {
        new SpringApplication(configClass).run(args);
    }

    /**
     * Load the Spring service and use Tomcat to run the web project.
     */
    public static void run(String[] springLocations, String... args) {
        run(springLocations, null, args);
    }

    /**
     * Load the Spring service and use Tomcat to run the web project.
     */
    public static void run(String[] springLocations, String[] resources, String... args) {
        new SpringApplication(springLocations, resources).run(args);
    }

    /**
     * Load the Spring service, non-web project.
     */
    public static SpringApplication runApplication(Class<?> configClass, String... args) {
        SpringApplication application = new SpringApplication(configClass);
        application.runApplication(args);
        return application;
    }

    /**
     * Load the Spring service, non-web project.
     */
    public static SpringApplication runApplication(String[] springLocations, String... args) {
        return runApplication(springLocations, null, args);
    }

    /**
     * Load the Spring service, non-web project.
     */
    public static SpringApplication runApplication(String[] springLocations, String log4j, String... args) {
        SpringApplication application = new SpringApplication();
        application.springLocations = springLocations;
        application.log4j = log4j;
        application.runApplication(args);
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

                // shutdown on Spring
                if (ContextSupport.getApplicationContext() instanceof AbstractApplicationContext)
                    ((AbstractApplicationContext) ContextSupport.getApplicationContext()).close();

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
                .addConfigClass(configClass)
                .addSpringFile(springLocations)
                .addResource(resources)
                .startup();
    }

    // non-web project
    private void runApplication(String... args) {
        synchronized (lock) {
            if (StringUtils.isNotEmpty(log4j)) {
                URL url = ClassUtils.getResource(configClass, log4j);

                // jar in jar
                InputStream log4jInputStream;
                if (Objects.isNull(url)) {
                    log4jInputStream = JarFileUtils.getJarEntryStream(
                            WebServerConfig.JAR_IN_CLASSES + IOUtils.DIR_SEPARATOR + log4j);
                } else
                    log4jInputStream = ClassUtils.getResourceAsStream(configClass, log4j);

                try {
                    // load log4j2
                    org.apache.logging.log4j.core.config.Configurator.initialize(
                            null,
                            new org.apache.logging.log4j.core.config.ConfigurationSource(log4jInputStream));

                    // load logback
//                    ch.qos.logback.classic.LoggerContext.LoggerContext context =
//                            (ch.qos.logback.classic.LoggerContextLoggerContext) LoggerFactory.getILoggerFactory();
//                    context.reset();
//                    ch.qos.logback.classic.joran.JoranConfiguratorJoranConfigurator configurator = new ch.qos.logback.classic.joran.JoranConfiguratorJoranConfigurator();
//                    configurator.setContext(context);
//                    configurator.doConfigure(log4jInputStream);

                    // load log4j 1.x
                    //org.apache.log4j.PropertyConfigurator.configure(log4jInputStream);

                    log.info("Logger was reset and started successfully!");
                } catch (Exception e) {
                    ThrowProvider.doThrow(e);
                } finally {
                    org.apache.commons.io.IOUtils.closeQuietly(log4jInputStream);
                }
            }

            // load spring
            if (ArrayUtils.isEmpty(springLocations))
                ContextSupport.initSpringByAnnotationClass(this.configClass);
            else
                ContextSupport.initSpringByClassPathFile(springLocations);
        }
    }

}
