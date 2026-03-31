package org.mind.framework.helper.delayqueue;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.service.threads.ExecutorFactory;
import org.redisson.api.Message;
import org.redisson.api.MessageArgs;
import org.redisson.api.RMapCache;
import org.redisson.api.RReliableQueue;
import org.redisson.api.RedissonClient;
import org.redisson.api.queue.AcknowledgeMode;
import org.redisson.api.queue.QueueAckArgs;
import org.redisson.api.queue.QueueAddArgs;
import org.redisson.api.queue.QueuePollArgs;
import org.redisson.api.queue.QueueRemoveArgs;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Redisson 可靠队列服务（基于 RReliableQueue, 仅限Redisson PRO）
 *
 * <p>相对于已废弃的 RDelayedQueue，RReliableQueue 提供：
 * <ul>
 *   <li>原生延迟投递（MessageArgs.delay）</li>
 *   <li>消息可见性超时（Visibility Timeout）：消费者崩溃时自动重投</li>
 *   <li>手动 ACK / NACK 机制，保证 at-least-once 语义</li>
 *   <li>投递次数上限（deliveryLimit）+ 死信队列（DLQ）支持</li>
 * </ul>
 *
 * <p>与旧版的关键差异：
 * <ul>
 *   <li>入队：{@code add(QueueAddArgs)} 替代 {@code offer(task, delay, timeUnit)}</li>
 *   <li>出队：{@code poll(QueuePollArgs)} 返回 {@code Message<T>}，需手动提取 {@code getPayload()}</li>
 *   <li>删除：依赖 msgId，入队时会将 msgId 持久化到 rMapCache 供后续删除使用</li>
 *   <li>无需调用 {@code destroy()}，不依赖额外的 RBlockingQueue</li>
 * </ul>
 *
 * @author Marcus
 * @version 2.0
 * @date 2026/3/31
 */
@Slf4j
public class RedissonReliableQueueService {

    /**
     * rMapCache 中存储 msgId 的 key 前缀，格式：{@code msgid:{taskId}}
     */
    private static final String MSG_ID_KEY_PREFIX = "rr:msgid:";

    /**
     * 消息可见性超时（默认 30 秒）：消费者在此时间内未 ACK，消息自动重新入队
     */
    private static final Duration DEFAULT_VISIBILITY = Duration.ofSeconds(30);

    /**
     * 长轮询等待时长：替代原 blockingQueue.poll(1, SECONDS)
     */
    private static final Duration POLL_WAIT_TIMEOUT = Duration.ofSeconds(1);

    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);

    @Getter
    private final String queueName;

    @Getter
    private final String mapName;

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
     * 支持按 class type 和 task id 添加映射
     */
    private final Map<Object, Consumer<?>> queueConsumerMap = new ConcurrentHashMap<>();

    /**
     * 存储正在运行的队列监听器
     */
    private final Map<String, Future<?>> runningListenerMap = new ConcurrentHashMap<>();

    /**
     * 缓存查找到的兼容数据类型
     */
    private final Map<Class<?>, Consumer<?>> compatibleTypeReference = new ConcurrentHashMap<>();

    /**
     * RReliableQueue：统一承担入队、出队、ACK 职责
     * 替代原来的 RBlockingQueue + RDelayedQueue 组合
     */
    private final RReliableQueue<Object> reliableQueue;

    /**
     * 存储任务对象（以 taskId 为 key）及 msgId（以 "msgid:{taskId}" 为 key）
     */
    private final RMapCache<String, Object> rMapCache;

    @Getter
    private volatile boolean running;


    public RedissonReliableQueueService(String queueName, String mapName) {
        this.queueName = queueName;
        this.mapName = mapName;

        RedissonClient redissonClient = RedissonHelper.getClient();
        this.reliableQueue = redissonClient.getReliableQueue(queueName);
        this.rMapCache = redissonClient.getMapCache(mapName);

        if (SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            RedissonHelper.getInstance().addShutdownEvent(client -> {
                stopQueueListener();

                // task shutdown graceful
                shutdownExecutor(taskExecutor, "ReliableTask-Graceful", 10);

                // listener shutdown graceful
                shutdownExecutor(listenerExecutor, "ReliableListener-Graceful", 5);

                runningListenerMap.clear();
                queueConsumerMap.clear();
                compatibleTypeReference.clear();
            });
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
     * 任务对象必须可序列化（实现 Serializable 接口）
     *
     * <p>入队后会返回 {@code Message}，其 msgId 将持久化到 rMapCache，用于后续 remove 操作。
     *
     * @param task     任务对象
     * @param delay    延迟时间
     * @param timeUnit 时间单位
     * @param <T>      任务类型
     */
    public <T> boolean addDelayTask(T task, long delay, TimeUnit timeUnit) {
        if (Objects.isNull(task))
            return false;

        String taskId = null;
        try {
            Duration delayDuration = Duration.of(delay, timeUnit.toChronoUnit());

            // 构建 MessageArgs，支持原生延迟投递
            MessageArgs<Object> msgArgs =
                    MessageArgs.payload((Object) task).delay(delayDuration);

            Message<Object> msg = this.reliableQueue.add(QueueAddArgs.messages(msgArgs));

            if (task instanceof AbstractTask at) {
                taskId = at.getTaskId();
                // 持久化任务对象（供 get(taskId) 查询，TTL 与延迟时间一致）
                this.rMapCache.fastPut(taskId, task, delay, timeUnit);

                // 持久化 msgId（供 remove(taskId) 使用，无 TTL，由消费时或 remove 时清理）
                this.rMapCache.fastPut(MSG_ID_KEY_PREFIX + taskId, msg.getId());
            }

            if (log.isDebugEnabled()) {
                log.debug("Added a reliable task successfully, task type: {}, msgId: {}, delay: {} {}",
                        task.getClass().getSimpleName(), msg.getId(), delay, timeUnit);
            }

            // 确保该队列有对应类型的消费者在运行
            ensureQueueListenerRunning();
            return true;
        } catch (Exception e) {
            // Rollback：如果队列添加失败，清理 map
            if (StringUtils.isNotEmpty(taskId)) {
                this.rMapCache.fastRemove(taskId);
                this.rMapCache.fastRemove(MSG_ID_KEY_PREFIX + taskId);
            }
            log.error("Failed to add a reliable task, task: {}, error: {}", task, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 添加延迟任务并注册消费者
     *
     * @param task     任务对象（必须可序列化）
     * @param delay    延迟时间
     * @param timeUnit 时间单位
     * @param consumer 消费者处理逻辑
     * @param <T>      任务类型
     */
    @Deprecated
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
     * <p>内部通过 rMapCache 查找入队时持久化的 msgId，再从 RReliableQueue 中移除。
     * 仅支持实现了 {@link AbstractTask} 的任务类型（需有 taskId）。
     *
     * @param task 任务对象
     * @param <T>  任务类型
     * @return 是否删除成功
     */
    public <T> boolean remove(T task) {
        if (Objects.isNull(task))
            return false;

        if (!(task instanceof AbstractTask at)) {
            log.warn("Remove(task) only supports AbstractTask subtypes. task type: {}", task.getClass().getSimpleName());
            return false;
        }

        return remove(at.getTaskId());
    }

    /**
     * 按 taskId 删除延迟任务
     */
    public boolean remove(String taskId) {
        if (StringUtils.isEmpty(taskId))
            return false;

        try {
            // 从 rMapCache 取回入队时保存的 msgId
            String msgId = (String) this.rMapCache.get(MSG_ID_KEY_PREFIX + taskId);
            if (StringUtils.isEmpty(msgId)) {
                log.warn("Cannot remove task: msgId not found in cache, taskId: {}", taskId);
                return false;
            }

            boolean result = this.reliableQueue.remove(QueueRemoveArgs.ids(msgId));
            if (result) {
                this.rMapCache.fastRemove(taskId);
                this.rMapCache.fastRemove(MSG_ID_KEY_PREFIX + taskId);
            }

            log.info("Delete reliable-queue task: [{}], taskId: {}, msgId: {}",
                    result ? "OK" : "Failed", taskId, msgId);
            return result;
        } catch (Exception e) {
            log.error("Failed to delete reliable task, taskId: {}, error: {}", taskId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取延迟任务的对象（通过 taskId 从 MapCache 查询）
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
        boolean registerTaskType = ArrayUtils.isEmpty(onlyTaskId) || !onlyTaskId[0];
        Class<?> clazz = task.getClass();

        if (registerTaskType) {
            if (queueConsumerMap.containsKey(clazz))
                log.warn("Type overridden, task type: {}", clazz.getSimpleName());

            // class type
            queueConsumerMap.put(task.getClass(), consumer);

            // 清除兼容类型缓存，防止后续查找时路由失效
            this.compatibleTypeReference.clear();
        }

        if (queueConsumerMap.containsKey(task.getTaskId()))
            log.warn("Same taskId overridden, task id: {}, override type: {}", task.getTaskId(), clazz.getSimpleName());

        // task id
        queueConsumerMap.put(task.getTaskId(), consumer);

        // 启动队列监听器（如果尚未启动）
        ensureQueueListenerRunning();
    }

    public <T> void registerConsumer(Class<T> taskType, Consumer<T> consumer) {
        if (queueConsumerMap.containsKey(taskType))
            log.warn("Type overridden, task type: {}", taskType.getSimpleName());

        // class type
        queueConsumerMap.put(taskType, consumer);

        // 清除兼容类型缓存，防止后续查找时路由失效
        this.compatibleTypeReference.clear();

        // 启动队列监听器（如果尚未启动）
        ensureQueueListenerRunning();
    }

    /**
     * 确保队列监听器正在运行
     */
    private void ensureQueueListenerRunning() {
        if (isRunning() && isListenerActuallyRunning())
            return;

        synchronized (this) {
            if (isRunning() && isListenerActuallyRunning())
                return;

            this.running = true;

            Future<?> future = getListenerExecutor().submit(() -> {
                try {
                    startQueueListener(this.queueName);
                } catch (Exception e) {
                    log.error("Listener thread crashed unexpectedly", e);
                    runningListenerMap.remove(this.queueName);
                    this.running = false;
                }
            });
            runningListenerMap.put(this.queueName, future);
            log.debug("Start the Reliable-Queue listener ....");
        }
    }

    /**
     * 检查监听器是否真的在运行
     */
    private boolean isListenerActuallyRunning() {
        Future<?> future = runningListenerMap.get(this.queueName);
        if (Objects.isNull(future))
            return false;

        if (future.isDone()) {
            log.warn("Listener future is done, removing from map");
            runningListenerMap.remove(this.queueName);
            return false;
        }

        return true;
    }

    /**
     * 启动队列监听器
     *
     * <p>使用 RReliableQueue 的长轮询替代原来的 blockingQueue.poll()。
     * 出队得到 {@code Message<Object>}，提取 payload 后交由消费者处理，处理完毕后手动 ACK。
     * 若消费者异常且未 ACK，消息将在 visibilityTimeout 超时后自动重新可见（at-least-once 语义）。
     */
    private void startQueueListener(String queueName) {
        QueuePollArgs pollArgs = QueuePollArgs.defaults()
                .visibility(DEFAULT_VISIBILITY)
                .acknowledgeMode(AcknowledgeMode.MANUAL)
                .timeout(POLL_WAIT_TIMEOUT);

        while (isRunning()) {
            try {
                // 长轮询：等待最多 POLL_WAIT_TIMEOUT，无消息返回 null
                Message<Object> msg = reliableQueue.poll(pollArgs);

                if (Objects.isNull(msg))
                    continue;

                Object task = msg.getPayload();
                String msgId = msg.getId();

                if (log.isDebugEnabled()) {
                    log.debug("Get reliable-queue task, task type: {}, msgId: {}",
                            task.getClass().getSimpleName(), msgId);
                }

                // 查找对应类型的消费者
                if (queueConsumerMap.isEmpty()) {
                    // 无消费者注册，不 ACK，等待可见性超时后自动重投
                    log.warn("No consumer map found for queue: {}, msgId: {} will re-enqueue after visibility timeout",
                            queueName, msgId);
                    continue;
                }

                Consumer<?> consumer = findConsumer(task);

                if (Objects.nonNull(consumer)) {
                    executeTask(task, msgId, consumer);
                } else {
                    // 无匹配消费者，直接 ACK 避免无限重投
                    ackQuietly(msgId);
                    log.warn("No matching consumer found for task type: {}, taskId: {}, msgId: {} (auto-acked)",
                            task.getClass().getSimpleName(),
                            (task instanceof AbstractTask at) ? at.getTaskId() : "N/A",
                            msgId);
                }
            } catch (Exception e) {
                log.error("Reliable-Queue listening error, queue name: {}, error: {}", queueName, e.getMessage(), e);
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
            log.info("Stopped the Reliable-Queue listener, queue name: {}", queueName);
        }
    }

    /**
     * 使用线程池异步处理任务，处理完成后手动 ACK
     *
     * <p>ACK 策略：
     * <ul>
     *   <li>成功：ACK，消息从队列彻底移除</li>
     *   <li>失败：不 ACK，visibilityTimeout 超时后消息重新可见并重投（at-least-once）</li>
     *   <li>若不希望失败重投，可在 catch 块中调用 ackQuietly 改为 at-most-once 语义</li>
     * </ul>
     */
    private void executeTask(Object task, String msgId, Consumer<?> consumer) {
        final String finalTaskId = (task instanceof AbstractTask at) ? at.getTaskId() : null;
        final Consumer<Object> finalConsumer = (Consumer<Object>) consumer;

        getTaskExecutor().execute(() -> {
            boolean ackSuccess = false;
            try {
                // 如处理失败，直接抛出异常实现重投
                finalConsumer.accept(task);

                // Processed OK, manual ACK
                ackSuccess = ackQuietly(msgId);

            } catch (Exception e) {
                log.error("Handle reliable-queue task exception, task: {}, msgId: {}, error: {}",
                        task, msgId, e.getMessage(), e);
                // 不 ACK：消息将在 visibilityTimeout 超时后自动重新入队 at-least-once
                // 如需 at-most-once 语义（失败也不重投）:
                // ackQuietly(msgId);
            } finally {
                // 只有 ACK 成功后再清理元数据，保证重投时仍能 remove/get
                if (ackSuccess && StringUtils.isNotEmpty(finalTaskId)) {
                    rMapCache.fastRemove(finalTaskId);
                    rMapCache.fastRemove(MSG_ID_KEY_PREFIX + finalTaskId);
                }
            }
        });
    }

    /**
     * 静默 ACK
     */
    private boolean ackQuietly(String msgId) {
        try {
            reliableQueue.acknowledge(QueueAckArgs.ids(msgId));
            return true;
        } catch (Exception e) {
            log.error("Failed to acknowledge message, msgId: {}, error: {}", msgId, e.getMessage(), e);
            return false;
        }
    }

    private Consumer<?> findConsumer(Object task) {
        // 优先级：TaskId > ClassType > AssignableType
        String taskId = (task instanceof AbstractTask at) ? at.getTaskId() : null;

        // 尝试 task id 匹配
        if (StringUtils.isNotEmpty(taskId)) {
            Consumer<?> consumer = queueConsumerMap.get(taskId);
            if (Objects.nonNull(consumer))
                return consumer;
        }

        // 尝试类型匹配
        Consumer<?> consumer = queueConsumerMap.get(task.getClass());
        if (Objects.nonNull(consumer))
            return consumer;

        // 尝试找到可兼容类型的消费者（继承关系匹配）
        return compatibleTypeReference.computeIfAbsent(task.getClass(), taskClass -> {
            for (Map.Entry<Object, Consumer<?>> entry : queueConsumerMap.entrySet()) {
                if (entry.getKey() instanceof Class<?> clazz && clazz.isAssignableFrom(taskClass)) {
                    return entry.getValue();
                }
            }
            return null;
        });
    }

    /**
     * 停止队列监听器
     * RReliableQueue 无需 destroy()，直接停止监听线程即可
     */
    public synchronized void stopQueueListener() {
        this.running = false;

        Future<?> future = runningListenerMap.remove(this.queueName);
        if (Objects.nonNull(future)) {
            future.cancel(true);
            log.info("Stopping the Reliable-Queue listener ....");
        }
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
                            ExecutorFactory.newThreadFactory("reliable-task-", false),
                            new ThreadPoolExecutor.CallerRunsPolicy());
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
                            ExecutorFactory.newThreadFactory("reliable-listen-", true),
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
            if (!executor.awaitTermination(awaitSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
                // 3. forced shutdown
                List<Runnable> droppedTasks = executor.shutdownNow();

                if (!droppedTasks.isEmpty())
                    log.warn("{} dropped {} tasks during forced shutdown", executorName, droppedTasks.size());

                // 4. wait task again
                if (!executor.awaitTermination(3, TimeUnit.SECONDS))
                    log.error("{} did not terminate after forced shutdown", executorName);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("{} shutdown failed unexpectedly", executorName, e);
        }
    }
}