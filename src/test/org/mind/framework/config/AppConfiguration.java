package org.mind.framework.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.spring.VelocityEngineFactoryBean;
import org.mind.framework.annotation.CacheLevel;
import org.mind.framework.annotation.EnableCache;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.cache.CaffeineCache;
import org.mind.framework.cache.LruCache;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.helper.broadcast.RedissonStreamBroadcastService;
import org.mind.framework.mail.service.EmailService;
import org.mind.framework.mail.service.EmailServiceImpl;
import org.mind.framework.service.queue.QueueConfig;
import org.mind.framework.service.queue.QueueLittle;
import org.mind.framework.service.queue.QueueService;
import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.util.PropertiesUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @author Marcus
 * @version 1.0
 * @date 2023/6/26
 */
@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
@RequiredArgsConstructor
@EnableCache(levels = {CacheLevel.LOCAL, CacheLevel.REDIS})
@PropertySource("classpath:frame.properties")
@ComponentScan(basePackages = {"org.mind.framework"})
public class AppConfiguration {

    @Value("${mind.cache.provider:caffeine}")
    private String provider;

    @Value("${mind.cache.capacity:1024}")
    private int capacity;

    @Value("${mind.cache.ttl:0}")
    private long ttl;

    @Bean(destroyMethod = "destroy")
    public Cacheable cacheable() {
        if ("caffeine".equalsIgnoreCase(provider)) {
            log.info("Local cache provider: Caffeine (capacity={}, timeout={}ms)", capacity, ttl);
            return CaffeineCache.of(capacity, ttl);
        }

        log.info("Local cache provider: LRU (capacity={}, timeout={}ms)", capacity, ttl);

        Cacheable cacheable = LruCache.initCache();
        cacheable.setCapacity(capacity);
        cacheable.setTimeout(ttl);
        return cacheable;
    }

    @Bean(destroyMethod = "destroy")
    public QueueService queueService() {
        QueueConfig config =
                new QueueConfig()
                        .setMinConsumerThreads(1)
                        .setMaxConsumerThreads(1);

        QueueLittle queue = new QueueLittle(config);
        queue.start();
        return queue;
    }

    @Bean
    public VelocityEngineFactoryBean velocityEngine() {
        VelocityEngineFactoryBean velocityEngine = new VelocityEngineFactoryBean();
        velocityEngine.setVelocityProperties(
                PropertiesUtils.getProperties(
                        ClassUtils.getResourceAsStream(AppConfiguration.class, "velocity.properties")));
        return velocityEngine;
    }


    @Bean
    public RedissonStreamBroadcastService cacheBroadcastService() {
        RedissonStreamBroadcastService broadcastService = new RedissonStreamBroadcastService("webmind");
        broadcastService.registerHandler(k -> {
            long delete = RedissonHelper.getClient().getKeys().delete(k);
            log.info("Receive Message: {}, {}", k, delete);
        });

        broadcastService.init();
        return broadcastService;
    }

    @Bean(value = "asyncExecutor", destroyMethod = "destroy")
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(0);
        taskExecutor.setMaxPoolSize(16);
        taskExecutor.setQueueCapacity(1024);

        taskExecutor.setThreadFactory(ExecutorFactory.newThreadFactory("async-spring-group", "async-task-"));
        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        taskExecutor.setAwaitTerminationSeconds(15);
        return taskExecutor;
    }

    @Bean(destroyMethod = "destroy")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);

        taskScheduler.setThreadNamePrefix("task-schedule-");
        taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        taskScheduler.setAwaitTerminationSeconds(15);
        taskScheduler.initialize();
        return taskScheduler;
    }

    @Bean
    public EmailService emailService(QueueService queueService) {
        EmailServiceImpl emailService = new EmailServiceImpl();
        emailService.setQueueService(queueService);
        return emailService;
    }
}
