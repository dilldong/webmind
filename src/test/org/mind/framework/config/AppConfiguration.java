package org.mind.framework.config;

import org.mind.framework.cache.Cacheable;
import org.mind.framework.cache.LruCache;
import org.mind.framework.service.MainService;
import org.mind.framework.service.UpdateLoopService;
import org.mind.framework.service.queue.ConsumerService;
import org.mind.framework.service.queue.QueueLittle;
import org.mind.framework.service.queue.QueueService;
import org.mind.framework.service.threads.ExecutorFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2023/6/26
 */
@Configuration
@EnableAsync
@EnableScheduling
@PropertySource("classpath:frame.properties")
@ComponentScan(basePackages = {"org.mind.framework"})
public class AppConfiguration {

    @Resource
    private Environment environment;

    @Bean(value = "cacheable", destroyMethod = "destroy")
    public Cacheable cacheable() {
        Cacheable cacheable = LruCache.initCache();
        cacheable.setCapacity(1024);
        return cacheable;
    }

    @Bean(value = "queueService", destroyMethod = "destroy")
    public QueueService queueService() {
        QueueLittle queue = new QueueLittle();
        queue.setQueueInstance(new LinkedBlockingQueue<>(1024));
        return queue;
    }

    @Bean(destroyMethod = "stop")
    public MainService mainService(QueueService queueService) {
        ConsumerService consumerQueue = new ConsumerService();
        consumerQueue.setUseThreadPool(true);
        //consumerQueue.setSubmitTask(10);
        //consumerQueue.setPoolSize(48);
//        consumerQueue.setTaskCapacity(2048);
        consumerQueue.setQueueService(queueService);
        consumerQueue.initExecutorPool();

        UpdateLoopService loopService = new UpdateLoopService();
        loopService.setServiceName("Loop-Queue-Svc");
        loopService.setSpaceTime(5L);
        loopService.setDaemon(true);
        loopService.addUpdater(consumerQueue);

        MainService mainService = new MainService();
        mainService.setChildServices(Collections.singletonList(loopService));
        return mainService;
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

    @Bean(value = "taskScheduler", destroyMethod = "destroy")
    public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(2);
        taskScheduler.setRemoveOnCancelPolicy(true);

//        taskScheduler.setThreadNamePrefix("task-schedule-");
        taskScheduler.setThreadFactory(ExecutorFactory.newThreadFactory("schedule-spring-group", "task-schedule-"));
        taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        taskScheduler.setAwaitTerminationSeconds(15);
        return taskScheduler;
    }
}
