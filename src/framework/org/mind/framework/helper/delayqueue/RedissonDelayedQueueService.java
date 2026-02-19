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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
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
 * @author Marcus
 * @version 1.0
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
    private final Map<String, Future<?>> runningListenerMap = new ConcurrentHashMap<>();

    /**
     * 缓存查找到的兼容数据类型
     */
    private final Map<Class<?>, Consumer<?>> compatibleTypeReference = new ConcurrentHashMap<>();

    private final RBlockingQueue<Object> blockingQueue;
    private final RDelayedQueue<Object> delayedQueue;
    private final RMapCache<String, Object> rMapCache;

    @Getter
    private volatile boolean running;

    @Getter
    private volatile boolean shutdownHookRegistered;

    public RedissonDelayedQueueService(String delayQueueName, String delayMapName) {
        this.delayQueueName = delayQueueName;
        this.delayMapName = delayMapName;

        RedissonClient redissonClient = RedissonHelper.getClient();
        this.blockingQueue = redissonClient.getBlockingQueue(delayQueueName);
        this.delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
        this.rMapCache = redissonClient.getMapCache(delayMapName);

        if (!shutdownHookRegistered) {
            synchronized (this) {
                if (!shutdownHookRegistered) {
                    RedissonHelper.getInstance().addShutdownEvent(client -> {
                        this.stopQueueListener();

                        // task shutdown graceful
                        shutdownExecutor(this.taskExecutor, "RDelayTask-Graceful", 10);

                        // listener shutdown graceful
                        shutdownExecutor(this.listenerExecutor, "RDelayListen-Graceful", 5);

                        runningListenerMap.clear();
                        queueConsumerMap.clear();
                        compatibleTypeReference.clear();
                    });
                    shutdownHookRegistered = true;
                }
            }
        }
    }

    public void init() {
        // 创建任务处理线程池
        initTaskExecutor();

        // 创建队列监听线程池
        initListenerExecutor();
    }

    /**
     * 添加延迟任务
     * 如果该队列已经注册了对应类型的消费者，会自动处理任务
     * 注意：任务对象必须可序列化（实现 Serializable 接口）
     *
     * @param task     任务对象（必须可序列化）
     * @param delay    延迟时间
     * @param timeUnit 时间单位
     * @param <T>      任务类型
     */
    public <T> boolean addDelayTask(T task, long delay, TimeUnit timeUnit) {
        if (Objects.isNull(task))
            return false;

        String taskId = null;
        boolean putable = false;
        try {
            if (task instanceof AbstractTask) {
                taskId = ((AbstractTask) task).getTaskId();
                putable = this.rMapCache.fastPut(taskId, task, delay, timeUnit);
                if (!putable) {
                    log.error("Failed to add a delay task, task: {}", task);
                    return false;
                }
            }

            this.delayedQueue.offer(task, delay, timeUnit);
            log.debug("Added a delay task successfully, task type: {}, delay time: {} {}",
                    task.getClass().getSimpleName(), delay, timeUnit);

            // 确保该队列有对应类型的消费者在运行
            ensureQueueListenerRunning();
            return true;
        } catch (Exception e) {
            // Rollback：如果队列添加失败，清理 map
            if (putable && StringUtils.isNotEmpty(taskId))
                this.rMapCache.fastRemove(taskId);

            log.error("Failed to add a delay task, task: {}, error: {}", task, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 添加延迟任务并注册消费者
     * 如果该队列没有注册过消费者，会同时注册消费者
     * 注意：任务对象必须可序列化（实现 Serializable 接口）
     *
     * @param task     任务对象（必须可序列化）
     * @param delay    延迟时间
     * @param timeUnit 时间单位
     * @param consumer 消费者处理逻辑
     * @param <T>      任务类型
     */
    public <T> boolean addDelayTask(T task, long delay, TimeUnit timeUnit, Consumer<T> consumer) {
        if (Objects.isNull(task))
            return false;

        // 注册消费者
        if (task instanceof DelayTask)
            registerConsumer((DelayTask<T>) task, (Consumer<DelayTask<T>>) consumer, true);
        else
            registerConsumer((Class<T>) task.getClass(), consumer);

        // 添加任务
        return addDelayTask(task, delay, timeUnit);
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
        Map<Object, Consumer<?>> consumers =
                queueConsumerMap.computeIfAbsent(this.delayQueueName, k -> new ConcurrentHashMap<>(16));

        boolean registerTaskType = ArrayUtils.isEmpty(onlyTaskId) || !onlyTaskId[0];
        Class<?> clazz = task.getClass();

        if (registerTaskType) {
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

    public <T> void registerConsumer(Class<T> taskType, Consumer<T> consumer) {
        Map<Object, Consumer<?>> consumers =
                queueConsumerMap.computeIfAbsent(this.delayQueueName, k -> new ConcurrentHashMap<>(16));

        if (consumers.containsKey(taskType))
            log.warn("Warn: Type overridden, task type{}", taskType.getSimpleName());

        // class type
        consumers.put(taskType, consumer);

        // 启动队列监听器(如果尚未启动)
        ensureQueueListenerRunning();
    }

    /**
     * 确保队列监听器正在运行
     */
    private void ensureQueueListenerRunning() {
        // 如果该队列的监听器已经在运行，直接返回
        if (isRunning() && isListenerActuallyRunning())
            return;

        synchronized (this) {
            // 再次检查(双检查DCL)
            if (isRunning() && isListenerActuallyRunning())
                return;

            // 启动队列监听器
            this.running = true;

            // 使用 submit 后保存 Future，方便 stop 时 cancel
            Future<?> future = getListenerExecutor().submit(() -> {
                try {
                    startQueueListener(this.delayQueueName);
                }catch (Exception e){
                    log.error("Listener thread crashed unexpectedly", e);
                    // 监听器崩溃，清理状态
                    runningListenerMap.remove(this.delayQueueName);
                    this.running = false;
                }
            });
            runningListenerMap.put(this.delayQueueName, future);
            log.debug("Start the Delay-Queue listener ....");
        }
    }

    /**
     * 检查监听器是否真的在运行
     */
    private boolean isListenerActuallyRunning() {
        Future<?> future = runningListenerMap.get(this.delayQueueName);
        if (future == null)
            return false;

        // 检查 Future 是否完成（完成意味着监听器已退出）
        if (future.isDone()) {
            log.warn("Listener future is done, removing from map");
            runningListenerMap.remove(this.delayQueueName);
            return false;
        }

        return true;
    }

    /**
     * 启动队列监听器
     *
     * @param queueName 队列名称
     */
    private void startQueueListener(String queueName) {
        while (isRunning()) {
            try {
                // 使用带超时的poll获取队列中的任务
                Object task = blockingQueue.poll(1L, TimeUnit.SECONDS);
                if (Objects.isNull(task))
                    continue;

                log.debug("Get delayed task, task type: {}", task.getClass().getSimpleName());

                // 查找对应类型的消费者
                Map<Object, Consumer<?>> consumers = queueConsumerMap.get(queueName);
                if (Objects.isNull(consumers))
                    continue;

                Consumer<?> consumer = findConsumer(task, consumers);

                // 执行任务处理
                if (Objects.nonNull(consumer)) {
                    executeTask(task, consumer);
                } else {
                    log.warn("No matching consumer found for task type: {}, taskId: {}",
                            task.getClass().getSimpleName(),
                            task instanceof AbstractTask ? ((AbstractTask) task).getTaskId() : "N/A");
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
        runningListenerMap.remove(queueName);
        synchronized (this) {
            this.running = false;
            log.info("Stopped the Delay-Queue listener，queue name: {}", queueName);
        }
    }

    // 使用线程池异步处理任务
    private void executeTask(Object task, Consumer<?> consumer){
        final Object finalTask = task;
        final String finalTaskId = (task instanceof AbstractTask) ? ((AbstractTask) task).getTaskId() : null;
        final Consumer<Object> finalConsumer = (Consumer<Object>) consumer;

        getTaskExecutor().execute(() -> {
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

    private Consumer<?> findConsumer(Object task, Map<Object, Consumer<?>> consumers) {
        // 优先级：TaskId > ClassType > AssignableType
        String taskId = (task instanceof AbstractTask) ? ((AbstractTask) task).getTaskId() : null;

        // 尝试task id匹配
        if(StringUtils.isNotEmpty(taskId)) {
            Consumer<?> consumer = consumers.get(taskId);
            if(Objects.nonNull(consumer))
                return consumer;
        }

        // 尝试类型匹配
        Consumer<?> consumer = consumers.get(task.getClass());
        if(Objects.nonNull(consumer))
            return consumer;

        // 尝试找到可兼容类型的消费者
        return compatibleTypeReference.computeIfAbsent(task.getClass(), taskClass -> {
            // 最后尝试继承关系匹配
            for (Map.Entry<Object, Consumer<?>> entry : consumers.entrySet()) {
                if (entry.getKey() instanceof Class && ((Class<?>) entry.getKey()).isAssignableFrom(taskClass)) {
                    return entry.getValue();
                }
            }

            return null;
        });
    }

    /**
     * 停止队列监听器
     */
    public synchronized void stopQueueListener() {
        this.running = false;

        Future<?> future = runningListenerMap.remove(this.delayQueueName);
        if (future != null) {
            future.cancel(true);
            log.info("Stopping the Delay-Queue listener ....");
        }

        if (Objects.nonNull(delayedQueue))
            delayedQueue.destroy();
    }

    private ThreadPoolExecutor getTaskExecutor() {
        if (Objects.isNull(taskExecutor))
            initTaskExecutor();

        return taskExecutor;
    }

    private ThreadPoolExecutor getListenerExecutor() {
        if (Objects.isNull(listenerExecutor))
            initListenerExecutor();

        return listenerExecutor;
    }

    private void initTaskExecutor() {
        if (Objects.isNull(taskExecutor)) {
            synchronized (this) {
                if (Objects.isNull(taskExecutor)) {
                    taskExecutor = ExecutorFactory.newThreadPoolExecutor(
                            2,
                            Math.min(Runtime.getRuntime().availableProcessors() << 1, 4),
                            60L, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(128),
                            ExecutorFactory.newThreadFactory("delay-task-", false),
                            new ThreadPoolExecutor.AbortPolicy());
                }
            }
        }
    }

    private void initListenerExecutor() {
        if (Objects.isNull(listenerExecutor)) {
            synchronized (this) {
                if (Objects.isNull(listenerExecutor)) {
                    listenerExecutor = ExecutorFactory.newThreadPoolExecutor(
                            1, 1,
                            60L, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(2),
                            ExecutorFactory.newThreadFactory("delay-listen-", true),
                            new ThreadPoolExecutor.CallerRunsPolicy());
                }
            }
        }
    }

    private void shutdownExecutor(ExecutorService executor, String executorName, long awaitSeconds) {
        if (Objects.isNull(executor) || executor.isShutdown())
            return;

        try {
            // 1. shutdown graceful
            executor.shutdown();

            // 2. wait task completed
            if (!executor.awaitTermination(awaitSeconds, TimeUnit.SECONDS)) {
                // 3. forced shutdown
                List<Runnable> droppedTasks = executor.shutdownNow();

                if (!droppedTasks.isEmpty())
                    log.warn("{} dropped [{}] tasks during forced shutdown", executorName, droppedTasks.size());

                // 4. wait task again
                if (!executor.awaitTermination(3, TimeUnit.SECONDS))
                    log.error("{} did not terminate even after forced shutdown", executorName);
            }
        } catch (InterruptedException e) {
            log.error("{} shutdown interrupted", executorName, e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("{} shutdown failed with unexpected error", executorName, e);
        }
    }
}
