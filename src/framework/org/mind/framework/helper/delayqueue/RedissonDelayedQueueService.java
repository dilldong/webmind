package org.mind.framework.helper.delayqueue;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.service.threads.ExecutorFactory;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Redisson延迟队列服务
 * 可用于处理延迟任务、过期处理等场景
 * 采用线程池模式自动处理队列任务
 *
 * @version 1.0
 * @author Marcus
 * @date 2025/5/22
 */
@Slf4j
public class RedissonDelayedQueueService {
    @Getter
    private final String delayQueueName;

    @Getter
    private final String delayMapName;

    /**
     * 任务处理线程池
     */
    @Setter
    private ThreadPoolExecutor taskExecutor;

    /**
     * 队列监听线程池
     */
    @Setter
    private ThreadPoolExecutor listenerExecutor;

    /**
     * 存储队列与消费者的映射关系
     * 支持按class type和task id添加映射
     */
    private final Map<String, Map<Object, Consumer<?>>> queueConsumerMap = new ConcurrentHashMap<>();

    /**
     * 存储正在运行的队列监听器
     */
    private final Map<String, Future<?>> runningListeners = new ConcurrentHashMap<>();

    private final RBlockingQueue<Object> blockingQueue;
    private final RDelayedQueue<Object> delayedQueue;
    private final RMapCache<String, Object> rMapCache;

    @Getter
    private volatile boolean running;

    public RedissonDelayedQueueService(String delayQueueName, String delayMapName) {
        this.delayQueueName = delayQueueName;
        this.delayMapName = delayMapName;

        RedissonClient redissonClient = RedissonHelper.getClient();
        this.blockingQueue = redissonClient.getBlockingQueue(delayQueueName);
        this.delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
        this.rMapCache = redissonClient.getMapCache(delayMapName);

        RedissonHelper.getInstance().addShutdownEvent(client -> {
            this.stopQueueListener();

            if (!taskExecutor.isShutdown())
                taskExecutor.shutdown();

            if (!listenerExecutor.isShutdown())
                listenerExecutor.shutdown();

            runningListeners.clear();
            queueConsumerMap.clear();
        });
    }

    public void init(){
        // 创建任务处理线程池
        if(Objects.isNull(taskExecutor)) {
            taskExecutor = ExecutorFactory.newThreadPoolExecutor(
                    2,
                    Math.min(Runtime.getRuntime().availableProcessors() << 1, 4),
                    60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(64),
                    ExecutorFactory.newThreadFactory("delay-task-", false),
                    new ThreadPoolExecutor.CallerRunsPolicy());
        }

        // 创建队列监听线程池
        if(Objects.isNull(listenerExecutor)) {
            listenerExecutor = ExecutorFactory.newThreadPoolExecutor(
                    1, 2,
                    30L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(32),
                    ExecutorFactory.newThreadFactory("delay-listen-", true),
                    new ThreadPoolExecutor.CallerRunsPolicy());
        }
    }

    /**
     * 添加延迟任务
     * 如果该队列已经注册了对应类型的消费者，会自动处理任务
     *
     * @param task     任务对象
     * @param delay    延迟时间
     * @param timeUnit 时间单位
     * @param <T>      任务类型
     */
    public <T> void addDelayTask(T task, long delay, TimeUnit timeUnit) {
        if (Objects.isNull(task))
            return;

        try {
            this.delayedQueue.offer(task, delay, timeUnit);
            log.debug("Adding a delay task successfully, task type: {}, delay time: {} {}",
                    task.getClass().getSimpleName(), delay, timeUnit);

            if (task instanceof AbstractTask)
                this.rMapCache.fastPut(((AbstractTask) task).getTaskId(), task, delay, timeUnit);

            // 确保该队列有对应类型的消费者在运行
            ensureQueueListenerRunning();
        } catch (Exception e) {
            log.error("Failed to add a delay task, task: {}, error: {}", task, e.getMessage(), e);
        }
    }

    /**
     * 添加延迟任务并注册消费者
     * 如果该队列没有注册过消费者，会同时注册消费者
     *
     * @param task     任务对象
     * @param delay    延迟时间
     * @param timeUnit 时间单位
     * @param consumer 消费者处理逻辑
     * @param <T>      任务类型
     */
    public <T> void addDelayTask(T task, long delay, TimeUnit timeUnit, Consumer<T> consumer) {
        if (Objects.isNull(task))
            return;

        // 注册消费者
        if (task instanceof DelayTask)
            registerConsumer((DelayTask<T>) task, (Consumer<DelayTask<T>>) consumer, true);
        else
            registerConsumer((Class<T>) task.getClass(), consumer);

        // 添加任务
        addDelayTask(task, delay, timeUnit);
    }

    /**
     * 删除延迟任务
     *
     * @param task 任务对象
     * @param <T>  任务类型
     * @return 是否删除成功
     */
    public <T> boolean remove(T task) {
        if (Objects.isNull(task))
            return false;

        try {
            boolean result = delayedQueue.remove(task);
            if (result && task instanceof AbstractTask)
                this.rMapCache.fastRemove(((AbstractTask) task).getTaskId());

            log.info("Delete delay-queue tasks: [{}], task: {}", result ? "OK" : "Failed", task);
            return result;
        } catch (Exception e) {
            log.error("Failed to delete delayed task, task: {}, error: {}", task, e.getMessage(), e);
            return false;
        }
    }


    /**
     * 删除延迟任务
     */
    public <T> boolean remove(String taskId) {
        T task = this.get(taskId);
        return remove(task);
    }

    /**
     * 获取延迟任务的对象
     */
    public <T> T get(String taskId) {
        if (StringUtils.isEmpty(taskId))
            return null;

        try {
            return (T) this.rMapCache.get(taskId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 注册队列消费者
     * 注册后系统会自动监听并处理队列中的任务
     */
    public <T> void registerConsumer(DelayTask<T> task, Consumer<DelayTask<T>> consumer, boolean... onlyTaskId) {
        synchronized (queueConsumerMap) {
            Map<Object, Consumer<?>> consumers =
                    queueConsumerMap.computeIfAbsent(this.delayQueueName, k -> new ConcurrentHashMap<>());

            boolean registTaskType = ArrayUtils.isEmpty(onlyTaskId) || !onlyTaskId[0];
            Class<?> clazz = task.getClass();

            if (registTaskType) {
                if (consumers.containsKey(clazz))
                    log.warn("Warn: Type overridden, task type{}", clazz.getSimpleName());

                // class type
                consumers.put(task.getClass(), consumer);
            }

            if (consumers.containsKey(task.getTaskId()))
                log.warn("Warn: Same taskId overridden, task id: {}, override type: {}", task.getTaskId(), clazz.getSimpleName());

            // task id
            consumers.put(task.getTaskId(), consumer);

            // 启动队列监听器(如果尚未启动)
            ensureQueueListenerRunning();
        }
    }

    public <T> void registerConsumer(Class<T> taskType, Consumer<T> consumer) {
        synchronized (queueConsumerMap) {
            Map<Object, Consumer<?>> consumers =
                    queueConsumerMap.computeIfAbsent(this.delayQueueName, k -> new ConcurrentHashMap<>());

            if (consumers.containsKey(taskType))
                log.warn("Warn: Type overridden, task type{}", taskType.getSimpleName());

            // class type
            consumers.put(taskType, consumer);

            // 启动队列监听器(如果尚未启动)
            ensureQueueListenerRunning();
        }
    }

    /**
     * 确保队列监听器正在运行
     */
    private void ensureQueueListenerRunning() {
        // 如果该队列的监听器已经在运行，直接返回
        if (isRunning() && runningListeners.containsKey(this.delayQueueName))
            return;

        synchronized (runningListeners) {
            // 再次检查(双重检查锁定模式)
            if (isRunning() && runningListeners.containsKey(this.delayQueueName))
                return;

            // 启动队列监听器
            this.running = true;
            Future<?> future = listenerExecutor.submit(() -> startQueueListener(this.delayQueueName));
            runningListeners.put(this.delayQueueName, future);
            log.debug("Start the Delay-Queue listener ....");
        }
    }

    /**
     * 启动队列监听器
     *
     * @param queueName 队列名称
     */
    private void startQueueListener(String queueName) {
        //RBlockingQueue<Object> blockingQueue = RedissonHelper.getClient().getBlockingQueue(queueName);

        while (isRunning()) {
            try {
                // 阻塞获取队列中的任务
                Object task = blockingQueue.take();
                log.debug("Get delayed task, task type: {}", task.getClass().getSimpleName());

                // 查找对应类型的消费者
                Map<Object, Consumer<?>> consumers = queueConsumerMap.get(queueName);
                if (Objects.isNull(consumers))
                    continue;

                Consumer<?> consumer = null;
                String taskId = null;

                // 尝试task id匹配
                if (task instanceof AbstractTask) {
                    taskId = ((AbstractTask) task).getTaskId();
                    consumer = consumers.get(taskId);
                }

                // 尝试类型匹配
                if (Objects.isNull(consumer))
                    consumer = consumers.get(task.getClass());

                // 如果没有匹配，尝试找到可兼容类型的消费者
                if (Objects.isNull(consumer)) {
                    for (Map.Entry<Object, Consumer<?>> entry : consumers.entrySet()) {
                        if (entry.getKey() instanceof Class
                                && ((Class<?>) entry.getKey()).isAssignableFrom(task.getClass())) {
                            consumer = entry.getValue();
                            break;
                        }
                    }
                }

                // 执行任务处理
                if (Objects.nonNull(consumer)) {
                    final Consumer<Object> finalConsumer = (Consumer<Object>) consumer;
                    final Object finalTask = task;
                    final String finalTaskId = taskId;

                    // 使用线程池异步处理任务
                    taskExecutor.execute(() -> {
                        try {
                            if (StringUtils.isNotEmpty(finalTaskId))
                                rMapCache.fastRemove(finalTaskId);

                            finalConsumer.accept(finalTask);
                        } catch (Exception e) {
                            log.error("Handle delay-queue task exceptions, task: {}, error: {}",
                                    finalTask, e.getMessage(), e);
                        }
                    });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Delay-Queue listener is interrupted，queue name: {}", queueName);
                break;
            } catch (Exception e) {
                log.error("Delay-Queue listening error, queue name: {}, error: {}", queueName, e.getMessage(), e);
                // 短暂休眠避免异常情况下的死循环占用CPU
                try {
                    TimeUnit.SECONDS.sleep(1L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 监听器退出，从运行列表中移除
        runningListeners.remove(queueName);
        log.info("Stopped the Delay-Queue listener，queue name: {}", queueName);
    }

    /**
     * 停止队列监听器
     */
    public void stopQueueListener() {
        this.running = false;
        Future<?> future = runningListeners.remove(this.delayQueueName);
        if (future != null) {
            future.cancel(true);
            log.info("Stopping the Delay-Queue listener ....");
        }
    }
}
