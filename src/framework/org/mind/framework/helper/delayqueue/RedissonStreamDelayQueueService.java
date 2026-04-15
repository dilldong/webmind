package org.mind.framework.helper.delayqueue;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.ThreadUtils;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.service.Updatable;
import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.util.DateUtils;
import org.redisson.api.AutoClaimResult;
import org.redisson.api.PendingEntry;
import org.redisson.api.RMapCache;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RScript;
import org.redisson.api.RSetCache;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamPendingRangeArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.codec.StringCodec;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 基于 RStream + RScoredSortedSet 的延迟队列服务
 *
 * <h3>架构</h3>
 * <pre>
 *  addDelayTask()
 *      │
 *      ├─► RMapCache        存储 taskId → task 序列化体（含 TTL）
 *      └─► RScoredSortedSet 延迟调度索引（score = 触发时间戳 ms）
 *
 *  Scheduler Thread（每 200 ms）
 *      └─► Lua 原子脚本：ZRANGEBYSCORE + ZREM + XADD
 *                           │
 *                           ▼
 *                       RStream（Consumer Group）
 *                           │
 *  Consumer Thread ─────────┘
 *      ├─► 检查 RSet(cancelled)，已取消则 ACK 跳过
 *      ├─► 从 RMapCache 取 task
 *      ├─► findConsumer()（优先级：taskId > class > assignable）
 *      └─► TaskExecutor：业务回调成功 → ACK, 失败 → 留在 PEL 等待重投
 *
 *  PEL Monitor（Consumer Thread 内联）
 *      └─► autoClaim 30s 未 ACK 的消息，重新投递
 * </pre>
 *
 * <h3>对比原 RedissonDelayedQueueService 方案</h3>
 * <ul>
 *   <li>原方案：消费者 poll 成功后崩溃 → 任务丢失（无 ACK 机制）</li>
 *   <li>新方案：未 ACK 的消息驻留 PEL，30s 后自动 autoClaim 重投，保证 at-least-once</li>
 * </ul>
 *
 * <h3>命名约定（baseName = 构造参数）</h3>
 * <pre>
 *   {baseName}:zset       RScoredSortedSet，延迟调度
 *   {baseName}:stream     RStream，可靠消费
 *   {baseName}:map        RMapCache，task 存储
 *   {baseName}:cancelled  RSetCache，取消标记
 * </pre>
 *
 * @author Marcus
 * @version 2.0
 * @date 2026/3/31
 */
@Slf4j
public class RedissonStreamDelayQueueService {

    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);

    /**
     * Stream 消息体中存放 taskId 的字段名
     */
    private static final String STREAM_FIELD_TASK_ID = "taskId";

    /**
     * Stream Consumer Group 名称（同一 baseName 下所有实例共享同一 Group，确保消息不重复投递）
     */
    private static final String CONSUMER_GROUP = "rz-delay-group";

    /**
     * ZSet 扫描间隔（ms）：平衡调度精度与 Redis 压力
     */
    private static final long SCHEDULER_INTERVAL_MS = 200L;

    /**
     * ZSet 单次扫描最大任务数（防止单批次过大阻塞调度线程）
     */
    private static final int PROMOTE_BATCH_SIZE = 30;

    /**
     * PEL 消息超过此空闲时长（ms）后触发 autoClaim 重投
     */
    private static final long PEL_IDLE_THRESHOLD_MS = 30_000L;

    /**
     * 原子提升脚本：将 ZSet 中到期的 taskId 批量迁移到 Stream。
     *
     * <p>ZRANGEBYSCORE + ZREM + XADD 在单个 Redis 命令中执行，避免"ZREM 成功、XADD 失败"的数据丢失。
     *
     * <p>KEYS[1] = zsetKey，KEYS[2] = streamKey
     * <p>ARGV[1] = 当前时间戳 ms（string），ARGV[2] = 最大批量数
     */
    private static final String LUA_PROMOTE_SCRIPT =
            "local due = redis.call('ZRANGEBYSCORE', KEYS[1], 0, ARGV[1], 'LIMIT', 0, ARGV[2])\n" +
                    "for _, taskId in ipairs(due) do\n" +
                    "  redis.call('ZREM', KEYS[1], taskId)\n" +
                    "  redis.call('XADD', KEYS[2], '*', 'taskId', taskId)\n" +
                    "end\n" +
                    "return #due\n";

    /**
     * define: empty consumer
     */
    private static final Consumer<Object> NO_OP_CONSUMER = t -> {};

    @Getter
    private final String zsetKey;
    @Getter
    private final String streamKey;
    @Getter
    private final String mapKey;
    @Getter
    private final String cancelledKey;

    /**
     * 同一 JVM 实例内唯一的 consumer 名称，用于 Consumer Group 竞争消费
     */
    private final String consumerName;

    private final RScoredSortedSet<String> scoredSortedSet;
    private final RStream<String, String> rStream;
    private final RMapCache<String, Object> rMapCache;
    private final RSetCache<String> cancelledSet;
    private final RScript rScript;

    private final Map<Object, Consumer<?>> queueConsumerMap;

    private volatile Map<Class<?>, Consumer<?>> compatibleTypeReference;

    /**
     * 业务回调线程池（可由外部 setter 注入自定义线程池）
     */
    @Setter
    private volatile ThreadPoolExecutor taskExecutor;

    /**
     * Stream consumer 线程池（单线程）
     */
    @Setter
    private volatile ThreadPoolExecutor listenerExecutor;

    /**
     * ZSet 定时扫描线程池（单线程）
     */
    private volatile ThreadPoolTaskScheduler schedulerExecutor;

    @Getter
    private volatile boolean running;

    /**
     * @param baseName Redis key 前缀，如 {@code "payment:delay"}。
     *                 实际生成 4 个 key：{baseName}:zset / :stream / :map / :cancelled
     */
    public RedissonStreamDelayQueueService(String baseName) {
        this.zsetKey = baseName + ":zset";
        this.streamKey = baseName + ":stream";
        this.mapKey = baseName + ":map";
        this.cancelledKey = baseName + ":cancelled";
        this.consumerName = buildConsumerName();

        this.queueConsumerMap = new ConcurrentHashMap<>(16);
        this.replaceTypeCache();

        RedissonClient client = RedissonHelper.getClient();
        this.scoredSortedSet = client.getScoredSortedSet(zsetKey, StringCodec.INSTANCE);
        this.rStream = client.getStream(streamKey, StringCodec.INSTANCE);
        this.rMapCache = client.getMapCache(mapKey);
        this.cancelledSet = client.getSetCache(cancelledKey, StringCodec.INSTANCE);
        this.rScript = client.getScript(StringCodec.INSTANCE);

        // 尽早确保 Consumer Group 存在（幂等）
        ensureConsumerGroupExists();

        if (SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            RedissonHelper.getInstance().addShutdownEvent(c -> shutdown());
        }
    }

    /**
     * 初始化所有线程池并启动服务
     */
    public void init() {
        initTaskExecutor();
        initListenerExecutor();
        initSchedulerExecutor();
        ensureRunning();
    }

    /**
     * 添加延迟任务。
     *
     * <p>流程：MapCache.put(taskId, task, ttl) → ZSet.add(score=now+delay, taskId)。
     * 若任一步骤失败则回滚已写入的数据。
     *
     * <p>task 必须可序列化（实现 {@link java.io.Serializable}）。
     * 对于 {@link AbstractTask} 子类，使用 {@code at.getTaskId()} 作为 taskId；
     * 其他对象自动生成 UUID 作为 taskId。
     */
    public <T> boolean addDelayTask(T task, long delay, TimeUnit timeUnit) {
        if (Objects.isNull(task))
            return false;

        String taskId = (task instanceof AbstractTask) ? ((AbstractTask) task).getTaskId() : UUID.randomUUID().toString();
        long delayMillis = timeUnit.toMillis(delay);
        double triggerScore = DateUtils.CachedTime.currentMillis() + delayMillis;

        // MapCache: TTL = 延迟时间 + 24h（保证消费侧能取到task）
        long ttlMillis = delayMillis + DateUtils.ONE_DAY_MILLIS;

        try {
            rMapCache.fastPut(taskId, task, ttlMillis, TimeUnit.MILLISECONDS);
            scoredSortedSet.add(triggerScore, taskId);

            log.debug("Added delay task, type: {}, taskId: {}, delay: {} {}",
                    task.getClass().getSimpleName(), taskId, delay, timeUnit);

            ensureRunning();
            return true;

        } catch (Exception e) {
            // rollback
            safeRemoveMapCache(taskId);
            scoredSortedSet.remove(taskId);
            log.error("Failed to add delay task, task: {}, error: {}", task, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 添加延迟任务并同时注册消费者（向下兼容，已标记废弃，推荐先 registerConsumer 再 addDelayTask）。
     */
    @Deprecated
    public <T> boolean addDelayTask(T task, long delay, TimeUnit timeUnit, Consumer<T> consumer) {
        if (Objects.isNull(task))
            return false;

        if (task instanceof DelayTask)
            registerConsumer((DelayTask<T>) task, (Consumer<DelayTask<T>>) consumer, true);
        else
            registerConsumer((Class<T>) task.getClass(), consumer);

        return addDelayTask(task, delay, timeUnit);
    }

    /**
     * 删除延迟任务
     */
    public boolean remove(String taskId) {
        if (StringUtils.isEmpty(taskId))
            return false;

        try {
            boolean removedFromZset = scoredSortedSet.remove(taskId);

            if (removedFromZset) {
                // 任务尚未触发，直接清理
                safeRemoveMapCache(taskId);
                log.info("Removed task from ZSet, taskId: {}", taskId);
                return true;
            }

            // 任务已迁移到 Stream，标记为取消；Consumer 侧在 handleStreamMessage 中检查并跳过
            cancelledSet.add(taskId, DateUtils.ONE_DAY_MILLIS, TimeUnit.MILLISECONDS);
            log.info("Task already promoted to Stream, marked as cancelled, taskId: {}", taskId);
            return true;

        } catch (Exception e) {
            log.error("Failed to remove task, taskId: {}, error: {}", taskId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 查询任务（在任务被消费前有效，消费完成后 MapCache 中的记录会被清除）。
     */
    public <T> T get(String taskId) {
        if (StringUtils.isEmpty(taskId))
            return null;

        try {
            return (T) rMapCache.get(taskId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 注册 DelayTask 消费者。
     *
     * @param onlyTaskId 若为 {@code true}，仅注册 taskId 级路由（不注册 class 级路由）
     */
    public <T> void registerConsumer(DelayTask<T> task, Consumer<DelayTask<T>> consumer, boolean... onlyTaskId) {
        boolean registerType = ArrayUtils.isEmpty(onlyTaskId) || !onlyTaskId[0];
        if (registerType) {
            if (queueConsumerMap.containsKey(task.getClass()))
                log.warn("Consumer type overridden: {}", task.getClass().getSimpleName());

            queueConsumerMap.put(task.getClass(), consumer);
            replaceTypeCache();
        }

        if (queueConsumerMap.containsKey(task.getTaskId()))
            log.warn("Consumer taskId overridden: {}", task.getTaskId());

        queueConsumerMap.put(task.getTaskId(), consumer);
        ensureRunning();
    }

    /**
     * 注册 class 类型级别的消费者。
     */
    public <T> void registerConsumer(Class<T> taskType, Consumer<T> consumer) {
        if (queueConsumerMap.containsKey(taskType))
            log.warn("Consumer type overridden: {}", taskType.getSimpleName());

        queueConsumerMap.put(taskType, consumer);
        replaceTypeCache();
        ensureRunning();
    }


    /**
     * 核心修改：直接废弃旧 Map，赋予新 Map
     * 引用替换是原子的 (O(1))，不会阻塞任何正在进行 computeIfAbsent 的读取线程
     * 当注册消费者时，不再需要遍历清空旧数据，没有任何锁竞争
     */
    private void replaceTypeCache() {
        this.compatibleTypeReference = new ConcurrentHashMap<>(16);
    }

    /**
     * 停止所有后台线程（调度 + 消费）。优雅退出：先停止新任务入队，再等待执行中的任务完成。
     */
    public synchronized void stopQueueListener() {
        if (!running)
            return;

        this.running = false;
        if (Objects.nonNull(schedulerExecutor))
            schedulerExecutor.shutdown();

        log.info("Stopped StreamDelayQueue, stream: {}", streamKey);
    }

    /**
     * 定时扫描 ZSet，将到期的 taskId 通过 Lua 脚本原子地迁移到 Stream。
     *
     * <p>原子性保证：ZRANGEBYSCORE + ZREM + XADD 在同一事务中执行，
     * 避免 ZREM 成功但 XADD 失败导致任务丢失。
     */
    private void promoteReadyTasks() {
        try {
            Object result = rScript.eval(
                    RScript.Mode.READ_WRITE,
                    LUA_PROMOTE_SCRIPT,
                    RScript.ReturnType.INTEGER,
                    Arrays.asList(zsetKey, streamKey),
                    String.valueOf(DateUtils.CachedTime.currentMillis()),
                    String.valueOf(PROMOTE_BATCH_SIZE));

            if (result instanceof Long) {
                Long count = (Long) result;
                if (count.compareTo(0L) > 0)
                    log.debug("Promoted {} task(s) to stream: {}", count, streamKey);
            }

        } catch (Exception e) {
            log.error("Failed to promote tasks from ZSet to Stream: {}", e.getMessage(), e);
        }
    }

    /**
     * Stream Consumer 主循环（运行在 listenerExecutor 中的单线程）。
     *
     * <p>每轮循环：
     * <ol>
     *   <li>autoClaim PEL 中空闲超过 30s 的消息（处理宕机/超时场景）</li>
     *   <li>xreadgroup 读取新消息（阻塞最多 500ms）</li>
     *   <li>分发到 taskExecutor 异步执行；执行成功后 ACK，失败留在 PEL 等待重投</li>
     * </ol>
     */
    private void startStreamConsumer() {
        while (running) {
            try {
                // 1. PEL 重投（处理上次执行未 ACK 的消息）
                reclaimStalePendingMessages();

                // 2. 读取新消息（从未投递的消息）
                Map<StreamMessageId, Map<String, String>> messages =
                        rStream.readGroup(
                                CONSUMER_GROUP, consumerName,
                                StreamReadGroupArgs.neverDelivered()
                                        .count(PROMOTE_BATCH_SIZE)
                                        .timeout(Duration.ofMillis(500)));

                if (Objects.isNull(messages) || messages.isEmpty())
                    continue;

                for (Map.Entry<StreamMessageId, Map<String, String>> entry : messages.entrySet()) {
                    handleStreamMessage(entry.getKey(), entry.getValue());
                }

            } catch (Exception e) {
                log.error("Stream consumer error, stream={}: {}", streamKey, e.getMessage(), e);
                ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
            }
        }

        log.info("Stream consumer stopped, stream: {}", streamKey);
    }

    /**
     * 处理单条 Stream 消息。
     *
     * <p>执行顺序：取消检查 → 从 MapCache 取task → 路由到消费者 → 分发到TaskExecutor。
     * 只有在 TaskExecutor 中业务回调成功后才 ACK，失败时消息留在 PEL 等待重投。
     */
    private void handleStreamMessage(StreamMessageId msgId, Map<String, String> fields) {
        String taskId = fields.get(STREAM_FIELD_TASK_ID);

        if (StringUtils.isEmpty(taskId)) {
            log.warn("Stream message {} has no taskId field, acking and skipping", msgId);
            ackSilently(msgId);
            return;
        }

        // remove(taskId) 在任务已进入 Stream 后会写入 cancelledSet
        if (cancelledSet.remove(taskId)) {
            log.info("Task {} was cancelled, skipping (msgId: {})", taskId, msgId);
            ackSilently(msgId);
            safeRemoveMapCache(taskId);
            return;
        }

        // 从 MapCache 取 task
        Object task = rMapCache.get(taskId);
        if (Objects.isNull(task)) {
            // 可能原因：TTL 过期（超过 24h 未消费）或重复消费后 Map 已清理
            log.warn("Task body not found for taskId: {} (msgId: {}), acking to avoid infinite loop", taskId, msgId);
            ackSilently(msgId);
            return;
        }

        // 查找消费者
        Consumer<?> consumer = findConsumer(task, taskId);
        if (Objects.isNull(consumer) || consumer == NO_OP_CONSUMER) {
            log.warn("No consumer for taskId: {}, type: {}, acking to avoid infinite loop",
                    taskId, task.getClass().getSimpleName());
            ackSilently(msgId);
            return;
        }

        // 分发（ACK 在业务回调成功后才执行）
        dispatchToTaskExecutor(msgId, taskId, task, consumer);
    }

    /**
     * 将任务投递到 TaskExecutor 异步执行。
     *
     * <p>ACK 时机：业务回调 {@code consumer.accept(task)} 正常返回后才调用 ACK，
     * 若抛出异常则不 ACK，消息留在 PEL，30s 后被 autoClaim 重投。
     *
     * <p>注意：若 TaskExecutor 队列满触发 {@link ThreadPoolExecutor.CallerRunsPolicy}，
     * 则 Consumer 线程会在此阻塞直到任务执行完成，期间不会读取新消息，起到背压作用。
     */
    private void dispatchToTaskExecutor(StreamMessageId msgId,
                                        String taskId,
                                        Object task,
                                        Consumer<?> consumer) {

        final Consumer<Object> finalConsumer = (Consumer<Object>) consumer;
        getTaskExecutor().execute(() -> {
            try {
                // 该任务已被其他消费者节点抢先处理并删除了, 这里利用 MapCache 充当幂等守卫
                if (!safeRemoveMapCache(taskId)) {
                    ackSilently(msgId);
                    return;
                }

                finalConsumer.accept(task);

                // 业务成功 → ACK + 清理 Map
                ackSilently(msgId);

                if (log.isDebugEnabled())
                    log.debug("Task executed and acked, taskId: {}", taskId);

            } catch (Exception e) {
                // 补偿: 重新放入 MapCache
                rMapCache.fastPut(taskId, task, DateUtils.ONE_DAY_MILLIS, TimeUnit.MILLISECONDS);

                // 业务失败 → 不 ACK，等待 PEL 重投
                log.error("Task execution failed, taskId: {}, will be redelivered: {}", taskId, e.getMessage(), e);
            }
        });
    }

    /**
     * 扫描 PEL（Pending Entry List），autoClaim 空闲超过 {@code PEL_IDLE_THRESHOLD_MS} 的消息并重新处理。
     *
     * <p>覆盖场景：
     * <ul>
     *   <li>任务分发到 TaskExecutor 后 JVM 崩溃，导致 ACK 未发出</li>
     *   <li>业务回调持续失败超过阈值时间</li>
     * </ul>
     */
    private void reclaimStalePendingMessages() {
        try {
            AutoClaimResult<String, String> result = rStream.autoClaim(
                    CONSUMER_GROUP, consumerName,
                    PEL_IDLE_THRESHOLD_MS, TimeUnit.MILLISECONDS,
                    StreamMessageId.MIN,
                    PROMOTE_BATCH_SIZE);

            if (Objects.isNull(result) || Objects.isNull(result.getMessages()) || result.getMessages().isEmpty())
                return;

            log.info("Reclaimed {} stale pending message(s) in stream: {}", result.getMessages().size(), streamKey);

            for (Map.Entry<StreamMessageId, Map<String, String>> entry : result.getMessages().entrySet()) {
                handleStreamMessage(entry.getKey(), entry.getValue());
            }

        } catch (Exception e) {
            // autoClaim 非关键路径, 失败时静默忽略, 等待下次循环重试
            log.warn("autoClaim skipped: {}", e.getMessage());
        }
    }

    /**
     * 启动时恢复本 consumer 自己遗留在 PEL 中的消息
     * <p>安全性：只认领归属于当前 consumerName 的消息，不会影响其他实例。
     * 前提：consumerName 必须是稳定值（不含随机后缀）
     */
    private void recoverOwnPendingMessages() {
        try {
            List<PendingEntry> pending = rStream.listPending(
                    StreamPendingRangeArgs.groupName(CONSUMER_GROUP)
                            .startId(StreamMessageId.MIN)
                            .endId(StreamMessageId.MAX)
                            .count(PROMOTE_BATCH_SIZE)
                            .consumerName(consumerName)
            );

            if (Objects.isNull(pending) || pending.isEmpty())
                return;

            log.info("Recovering {} pending messages on startup...", pending.size());

            StreamMessageId[] ids = pending.stream()
                    .map(PendingEntry::getId)
                    .toArray(StreamMessageId[]::new);

            // idle=0 表示不论空闲多久都强制认领，安全：只认领自己名下的消息
            Map<StreamMessageId, Map<String, String>> messages =
                    rStream.claim(CONSUMER_GROUP, consumerName, 0L, TimeUnit.MILLISECONDS, ids);

            if (Objects.isNull(messages) || messages.isEmpty())
                return;

            for (Map.Entry<StreamMessageId, Map<String, String>> entry : messages.entrySet()) {
                handleStreamMessage(entry.getKey(), entry.getValue());
            }

        } catch (Exception e) {
            log.warn("Startup PEL recovery failed: {}", e.getMessage());
        }
    }

    /**
     * 查找消费者，优先级：taskId 精确匹配 > class 精确匹配 > 兼容类型匹配。
     */
    private Consumer<?> findConsumer(Object task, String taskId) {
        // 1. taskId
        if (StringUtils.isNotEmpty(taskId)) {
            Consumer<?> consumer = queueConsumerMap.get(taskId);
            if (Objects.nonNull(consumer))
                return consumer;
        }

        // 2. class
        Consumer<?> consumer = queueConsumerMap.get(task.getClass());
        if (Objects.nonNull(consumer))
            return consumer;

        // 将 volatile 变量读取到局部变量中，保证在整个方法周期内使用的是同一个 Map 实例
        Map<Class<?>, Consumer<?>> localCache = this.compatibleTypeReference;

        // 3. 继承/接口兼容匹配（结果缓存）
        return localCache.computeIfAbsent(task.getClass(), cls -> {
            for (Map.Entry<Object, Consumer<?>> entry : queueConsumerMap.entrySet()) {
                if (entry.getKey() instanceof Class<?> && ((Class<?>) entry.getKey()).isAssignableFrom(cls))
                    return entry.getValue();
            }
            return NO_OP_CONSUMER;// 防止"未找到"下的高频穿透
        });
    }

    /**
     * DCL 确保后台线程只启动一次。
     */
    private void ensureRunning() {
        if (running)
            return;

        synchronized (this) {
            if (running)
                return;

            running = true;

            getListenerExecutor().execute(() -> {
                try {
                    // 启动时先恢复自己遗留的 PEL 消息
                    recoverOwnPendingMessages();
                    startStreamConsumer();
                } catch (Exception e) {
                    log.error("Stream consumer thread crashed unexpectedly", e);
                    running = false;
                }
            });

            log.info("Stream delay queue started, zSet: {}, stream: {}, consumer: {}",
                    zsetKey, streamKey, consumerName);
        }
    }

    /**
     * 确保 Consumer Group 存在（第一次使用时创建，已存在时幂等忽略）。
     */
    private void ensureConsumerGroupExists() {
        try {
            rStream.createGroup(
                    StreamCreateGroupArgs.name(CONSUMER_GROUP)
                            .id(StreamMessageId.ALL)// 从头开始消费
                            .makeStream());  // 若 Stream 不存在则自动创建（MKSTREAM 标志）
            log.info("Consumer group '{}' created for stream: {}", CONSUMER_GROUP, streamKey);
        } catch (Exception e) {
            // BUSYGROUP 是正常情况（多实例启动 or 重启），静默降级
            if (Strings.CS.contains(e.getMessage(), "BUSYGROUP"))
                log.debug("Consumer group '{}' already exists, skipping", CONSUMER_GROUP);
            else
                log.warn("Consumer group init failed for stream: {}, error: {}", streamKey, e.getMessage());
        }
    }

    private void shutdown() {
        stopQueueListener();
        shutdownExecutor(taskExecutor, "RStreamTask-Graceful", 10);
        shutdownExecutor(listenerExecutor, "RStreamListen-Graceful", 5);
        queueConsumerMap.clear();
        compatibleTypeReference = Collections.emptyMap();
    }

    private void ackSilently(StreamMessageId msgId) {
        try {
            // 1. 先 ACK（从 PEL 移除）
            rStream.ack(CONSUMER_GROUP, msgId);

            // 2. 再 XDEL（从 Stream 主数据删除）
            rStream.remove(msgId);
        } catch (Exception e) {
            log.warn("Failed to ACK message {}: {}", msgId, e.getMessage());
        }
    }

    private boolean safeRemoveMapCache(String taskId) {
        try {
            long removedCount = rMapCache.fastRemove(taskId);
            return removedCount > 0L;
        } catch (Exception e) {
            log.warn("Failed to remove taskId: {} from MapCache: {}", taskId, e.getMessage());
            return false;
        }
    }

    // 优先读环境变量, jvm启动参数, hostname, 最后未知兜底
    private static String buildConsumerName() {
        // environment
        String instanceId = System.getenv("APP_INSTANCE_ID");
        if (StringUtils.isNotBlank(instanceId))
            return instanceId;

        // jvm params
        instanceId = Updatable.getAppInstanceId();
        if (StringUtils.isNotBlank(instanceId))
            return instanceId;

        try {
            String host = InetAddress.getLocalHost().getHostName();
            return "consumer-" + host;
        } catch (Exception e) {
            return "consumer-unknown";
        }
    }

    private void initSchedulerExecutor() {
        schedulerExecutor = new ThreadPoolTaskScheduler();

        schedulerExecutor.setPoolSize(1);
        schedulerExecutor.setThreadNamePrefix("delay-scheduler-");
        schedulerExecutor.setDaemon(true);

        schedulerExecutor.setWaitForTasksToCompleteOnShutdown(true);
        schedulerExecutor.setAwaitTerminationSeconds(10);

        // init
        schedulerExecutor.initialize();

        // submit task
        schedulerExecutor.scheduleAtFixedRate(this::promoteReadyTasks, Duration.ofMillis(SCHEDULER_INTERVAL_MS));
    }

    private void initTaskExecutor() {
        if (Objects.nonNull(taskExecutor))
            return;

        synchronized (this) {
            if (Objects.isNull(taskExecutor)) {
                taskExecutor = ExecutorFactory.newThreadPoolExecutor(
                        2,
                        Math.min(Runtime.getRuntime().availableProcessors() << 1, 4),
                        60L, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(128),
                        ExecutorFactory.newThreadFactory("delay-task-", false),
                        new ThreadPoolExecutor.CallerRunsPolicy());
            }
        }
    }

    private void initListenerExecutor() {
        if (Objects.nonNull(listenerExecutor))
            return;

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

    private void shutdownExecutor(ExecutorService executor, String name, long awaitSeconds) {
        if (Objects.isNull(executor) || executor.isShutdown())
            return;

        try {
            // 1. shutdown graceful
            executor.shutdown();

            // 2. wait task completed
            if (!executor.awaitTermination(awaitSeconds, TimeUnit.SECONDS)) {
                // 3. forced shutdown
                List<Runnable> dropped = executor.shutdownNow();
                if (!dropped.isEmpty())
                    log.warn("{} dropped {} tasks during forced shutdown", name, dropped.size());

                // 4. wait task again
                if (!executor.awaitTermination(3L, TimeUnit.SECONDS))
                    log.error("{} did not terminate after forced shutdown", name);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("{} shutdown failed unexpectedly", name, e);
        }
    }
}