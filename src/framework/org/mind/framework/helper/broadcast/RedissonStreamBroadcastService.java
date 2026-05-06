package org.mind.framework.helper.broadcast;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.ThreadUtils;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.service.Updatable;
import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.util.DateUtils;
import org.redisson.api.PendingEntry;
import org.redisson.api.RStream;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamPendingRangeArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.api.stream.StreamTrimArgs;
import org.redisson.client.codec.StringCodec;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 基于 RStream 的广播服务
 *
 * <h3>与 RedissonStreamDelayQueueService 的关键区别</h3>
 * <pre>
 *  DelayQueueService:
 *    ZSet → Stream → 共享 ConsumerGroup（竞争消费，只有一个实例处理）
 *
 *  BroadcastService:
 *    直接 XADD → Stream → 每实例独立 ConsumerGroup（扇出，所有实例都处理）
 * </pre>
 *
 * <h3>架构</h3>
 * <pre>
 *  publish(payload)
 *      └─► XADD → {baseName}:broadcast:stream
 *
 *  实例 node-1:
 *      ConsumerGroup "broadcast:consumer-node-1" → 读取 → 回调 handler → ACK
 *
 *  实例 node-2:
 *      ConsumerGroup "broadcast:consumer-node-2" → 读取 → 回调 handler → ACK
 *
 *  PEL 恢复（启动时）：
 *      └─► listPending(自己的 group) → claim(idle=0) → 重新处理
 *
 *  Stream 自动裁剪（每 {trimIntervalMs} ms）：
 *      └─► XTRIM MAXLEN ~ {maxStreamLen}（近似裁剪，性能优先）
 * </pre>
 *
 * <h3>命名约定</h3>
 * <pre>
 *   {baseName}:broadcast:stream  → RStream
 *   ConsumerGroup 名称           → "broadcast:{consumerName}"（每实例唯一）
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 *  // 初始化（通常在 Spring Bean 中）
 *  RedissonStreamBroadcastService broadcastService =
 *      new RedissonStreamBroadcastService("cache:user");
 *
 *  // 注册处理器（L1 缓存失效逻辑）
 *  broadcastService.registerHandler(payload -> {
 *      localCache.invalidate(payload);
 *  });
 *
 *  broadcastService.init();
 *
 *  // 触发广播（缓存更新后调用）
 *  broadcastService.publish("user:profile:123");
 * }</pre>
 *
 * @author Marcus
 * @version 1.0
 * @date: 2026/5/6
 */
@Slf4j
public class RedissonStreamBroadcastService {
    private static final String FIELD_PAYLOAD = "payload";

    // 消费组前缀
    private static final String GROUP_PREFIX = "broadcast:";

    private static final int BATCH_SIZE = 50;
    private static final long READ_BLOCK_MS = 500L;

    // PEL 恢复：启动时认领自己遗留的未 ACK 消息
    private static final int PEL_RECOVER_LIMIT = 100;

    // Stream 裁剪：防止无限增长
    private static final long DEFAULT_MAX_STREAM_LEN = 10_000L;
    private static final long DEFAULT_TRIM_INTERVAL_MS = 60_000L;

    @Getter
    private final String streamKey;

    /**
     * 本实例的 consumerName，同时作为 ConsumerGroup 名称的后缀。
     * 与 DelayQueueService 保持相同的生成逻辑：环境变量 > hostname > fallback。
     */
    private final String consumerName;

    /**
     * 本实例独有的 ConsumerGroup 名称：{@code "broadcast:{consumerName}"}
     */
    private final String consumerGroup;

    private final RStream<String, String> rStream;

    /**
     * 业务处理器，由调用方通过 {@link #registerHandler} 注入。
     * payload = 发布时传入的字符串（如 cacheKey）。
     */
    private volatile Consumer<String> broadcastHandler;

    /**
     * 消费线程池（单线程，与 DelayQueueService 保持一致）
     */
    @Setter
    private volatile ThreadPoolExecutor listenerExecutor;

    @Getter
    private volatile boolean running;

    /**
     * Stream 最大保留条数（近似）。超过后自动裁剪旧消息。
     * 建议设置为：实例数 × 预期高峰消息速率 × 最长消费延迟秒数 的若干倍。
     */
    @Setter
    private long maxStreamLen = DEFAULT_MAX_STREAM_LEN;

    /**
     * Stream 裁剪间隔（ms）。
     */
    @Setter
    private long trimIntervalMs = DEFAULT_TRIM_INTERVAL_MS;

    private volatile long lastTrimTime = 0L;

    /**
     * @param baseName Redis key 前缀，如 {@code "cache:user"}。
     *                 实际 Stream key 为 {@code "{baseName}:broadcast:stream"}。
     */
    public RedissonStreamBroadcastService(String baseName) {
        this.streamKey = baseName + ":broadcast:stream";
        this.consumerName = buildConsumerName();
        this.consumerGroup = GROUP_PREFIX + consumerName;

        this.rStream = RedissonHelper.getClient().getStream(streamKey, StringCodec.INSTANCE);

        // 尽早建立 ConsumerGroup（幂等），MKSTREAM 自动创建 Stream
        ensureConsumerGroupExists();

        RedissonHelper.getInstance().addShutdownEvent(c -> shutdown());
    }

    /**
     * 注册广播消息处理器。
     *
     * @param handler 接收 payload 字符串（如 cacheKey），执行 L1 失效等操作。
     */
    public void registerHandler(Consumer<String> handler) {
        this.broadcastHandler = handler;
        ensureRunning();
    }

    /**
     * 初始化线程池并启动消费循环
     */
    public void init() {
        initListenerExecutor();
        ensureRunning();
    }

    /**
     * 发布广播消息，所有在线实例均会收到。
     *
     * <p>直接 XADD，不经过 ZSet（delay = 0 语义）。
     *
     * @param payload 广播内容，如: cacheKey。
     */
    public void publish(String payload) {
        if (StringUtils.isEmpty(payload)) {
            log.warn("[Broadcast] publish called with empty payload, ignored");
            return;
        }

        try {
            rStream.add(null, StreamAddArgs.entry(FIELD_PAYLOAD, payload));
        } catch (Exception e) {
            log.error("[Broadcast] Failed to publish payload: {}, error: {}", payload, e.getMessage(), e);
        }
    }

    /**
     * 停止消费循环
     */
    public synchronized void stopListener() {
        if (!running)
            return;

        this.running = false;
        log.info("[Broadcast] Listener stopped, stream: {}", streamKey);
    }

    /**
     * 手动移除本实例的 ConsumerGroup, 仅在需要强制清理时调用。
     */
    public void removeConsumerGroup() {
        try {
            rStream.removeGroup(consumerGroup);
            log.info("[Broadcast] ConsumerGroup removed: {}", consumerGroup);
        } catch (Exception e) {
            log.warn("[Broadcast] Failed to remove ConsumerGroup: {}, error: {}", consumerGroup, e.getMessage());
        }
    }

    /**
     * DCL 确保消费线程只启动一次（与 DelayQueueService 保持一致的模式）。
     */
    private void ensureRunning() {
        if (running)
            return;

        synchronized (this) {
            if (running)
                return;

            running = true;

            initListenerExecutor();
            getListenerExecutor().execute(() -> {
                try {
                    // 启动时先恢复本实例遗留在 PEL 中的消息
                    recoverOwnPendingMessages();
                    startStreamConsumer();
                } catch (Exception e) {
                    log.error("[Broadcast] Consumer thread crashed unexpectedly", e);
                    running = false;
                }
            });

            log.info("[Broadcast] Started, stream: {}, consumerGroup: {}", streamKey, consumerGroup);
        }
    }

    /**
     * Stream 消费主循环（运行在单线程中）
     *
     * <p>每轮：
     * <ol>
     *   <li>（每 {@code trimIntervalMs}）裁剪 Stream，防止无限增长</li>
     *   <li>xreadgroup 读取本 ConsumerGroup 未消费的新消息</li>
     *   <li>回调 handler → ACK；失败则不 ACK，由下次启动的 PEL 恢复重试</li>
     * </ol>
     *
     * <p><b>注意</b>：广播模式不使用 autoClaim，因为每个实例独享自己的 ConsumerGroup，
     * 不存在其他实例可以帮助"抢救"的情况。恢复依赖启动时的 {@link #recoverOwnPendingMessages()}。
     */
    private void startStreamConsumer() {
        while (running) {
            try {
                // 1. 按需裁剪 Stream
                maybeTrimmStream();

                // 2. 读取新消息
                Map<StreamMessageId, Map<String, String>> messages =
                        rStream.readGroup(
                                consumerGroup, consumerName,
                                StreamReadGroupArgs.neverDelivered()
                                        .count(BATCH_SIZE)
                                        .timeout(Duration.ofMillis(READ_BLOCK_MS)));

                if (Objects.isNull(messages) || messages.isEmpty())
                    continue;

                for (Map.Entry<StreamMessageId, Map<String, String>> entry : messages.entrySet()) {
                    handleMessage(entry.getKey(), entry.getValue());
                }

            } catch (Exception e) {
                log.error("[Broadcast] Consumer error, stream: {}, error: {}", streamKey, e.getMessage(), e);
                ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
            }
        }

        log.info("[Broadcast] Consumer loop exited, stream: {}", streamKey);
    }

    /**
     * 处理单条广播消息。
     *
     * <p>业务成功 → ACK + XDEL；失败 → 不 ACK（留在 PEL，重启后 {@link #recoverOwnPendingMessages()} 重投）。
     */
    private void handleMessage(StreamMessageId msgId, Map<String, String> fields) {
        String payload = fields.get(FIELD_PAYLOAD);

        if (StringUtils.isEmpty(payload)) {
            log.warn("[Broadcast] Message {} has no payload field, skipping", msgId);
            ackSilently(msgId);
            return;
        }

        Consumer<String> handler = this.broadcastHandler;
        if (Objects.isNull(handler)) {
            log.warn("[Broadcast] No handler registered, skipping message: {}", msgId);
            ackSilently(msgId);
            return;
        }

        try {
            handler.accept(payload);
            ackSilently(msgId);
        } catch (Exception e) {
            // 不 ACK → 重启时 PEL 恢复重投
            log.error("[Broadcast] Handler failed for payload: {}, msgId: {}, will retry on restart: {}",
                    payload, msgId, e.getMessage(), e);
        }
    }

    /**
     * 启动时恢复本实例自己遗留在 PEL 中的消息。
     *
     * <p>场景：服务崩溃导致 ACK 未发出，下次启动时用 idle=0 强制认领并重处理。
     * <p>安全性：仅操作本 ConsumerGroup 内、本 consumerName 名下的消息，不影响其他实例。
     * <p><b>前提</b>：consumerName 必须是稳定值（不含随机后缀），与 DelayQueueService 一致。
     */
    private void recoverOwnPendingMessages() {
        try {
            List<PendingEntry> pending = rStream.listPending(
                    StreamPendingRangeArgs.groupName(consumerGroup)
                            .startId(StreamMessageId.MIN)
                            .endId(StreamMessageId.MAX)
                            .count(PEL_RECOVER_LIMIT)
                            .consumerName(consumerName));

            if (Objects.isNull(pending) || pending.isEmpty())
                return;

            log.info("[Broadcast] Recovering {} pending message(s) for group: {}", pending.size(), consumerGroup);

            StreamMessageId[] ids = pending.stream()
                    .map(PendingEntry::getId)
                    .toArray(StreamMessageId[]::new);

            // idle=0：只要是自己名下的消息，不论空闲多久都强制认领
            Map<StreamMessageId, Map<String, String>> messages =
                    rStream.claim(consumerGroup, consumerName, 0L, TimeUnit.MILLISECONDS, ids);

            if (Objects.isNull(messages) || messages.isEmpty())
                return;

            for (Map.Entry<StreamMessageId, Map<String, String>> entry : messages.entrySet()) {
                handleMessage(entry.getKey(), entry.getValue());
            }

        } catch (Exception e) {
            log.warn("[Broadcast] Startup PEL recovery failed: {}", e.getMessage());
        }
    }

    /**
     * 按间隔裁剪 Stream，使用近似 MAXLEN（~ 符号）性能更优。
     *
     * <p><b>注意</b>：广播场景下，裁剪时需确保所有在线实例都已消费到足够旧的位置，
     * 否则可能裁掉未消费的消息。建议 {@code maxStreamLen} 设置得足够大（实例数 × 消息积压缓冲）。
     * 更精确的方案可查询各 ConsumerGroup 的 last-delivered-id，取最小值作为裁剪边界，
     * 但这会增加复杂度，通常 MAXLEN 已足够。
     */
    private void maybeTrimmStream() {
        long now = DateUtils.CachedTime.currentMillis();
        if (now - lastTrimTime < trimIntervalMs)
            return;

        lastTrimTime = now;
        try {
            // ~ 表示近似裁剪（非精确），性能优于精确 MAXLEN
            rStream.trim(StreamTrimArgs.maxLen((int) maxStreamLen).noLimit());
        } catch (Exception e) {
            log.warn("[Broadcast] Stream trim failed: {}", e.getMessage());
        }
    }

    /**
     * 确保本实例的 ConsumerGroup 存在。
     *
     * <p>使用 {@code StreamMessageId.ALL}（即 0-0）从头开始消费，
     * 保证实例首次上线时能消费到历史消息（在 Stream 未裁剪的范围内）。
     */
    private void ensureConsumerGroupExists() {
        try {
            rStream.createGroup(
                    StreamCreateGroupArgs.name(consumerGroup)
                            .id(StreamMessageId.ALL)
                            .makeStream());
            log.info("[Broadcast] ConsumerGroup created: {}, stream: {}", consumerGroup, streamKey);
        } catch (Exception e) {
            if (Strings.CS.contains(e.getMessage(), "BUSYGROUP"))
                log.info("[Broadcast] ConsumerGroup already exists: {}", consumerGroup);
            else
                log.warn("[Broadcast] ConsumerGroup init failed: {}, error: {}", consumerGroup, e.getMessage());
        }
    }

    private void ackSilently(StreamMessageId msgId) {
        try {
            // 1. 先 ACK（从 PEL 移除）
            rStream.ack(consumerGroup, msgId);

            // 2. 再 XDEL（从 Stream 主数据删除）
            rStream.remove(msgId);
        } catch (Exception e) {
            log.warn("[Broadcast] Failed to ACK message {}: {}", msgId, e.getMessage());
        }
    }

    private void shutdown() {
        stopListener();
        shutdownExecutor(listenerExecutor, "Broadcast-Graceful", 5L);
        // 本实例退出后无人继承，移除以防元数据积压
        removeConsumerGroup();
    }

    private ThreadPoolExecutor getListenerExecutor() {
        if (Objects.isNull(listenerExecutor))
            initListenerExecutor();

        return listenerExecutor;
    }

    // DCL
    private void initListenerExecutor() {
        if(Objects.isNull(listenerExecutor)) {
            synchronized (this) {
                if (Objects.isNull(listenerExecutor)) {
                    listenerExecutor = ExecutorFactory.newThreadPoolExecutor(
                            1, 1,
                            60L, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(2),
                            ExecutorFactory.newThreadFactory("broadcast-listen-", true),
                            new ThreadPoolExecutor.CallerRunsPolicy());
                }
            }
        }
    }

    /**
     * 与 DelayQueueService 相同的 consumerName 生成逻辑：
     * 环境变量（APP_INSTANCE_ID）> hostname > fallback。
     * 保持稳定，重启后 PEL 才能正确恢复。
     */
    private static String buildConsumerName() {
        String instanceId = Updatable.getAppInstanceId();
        if (StringUtils.isNotBlank(instanceId))
            return instanceId;

        try {
            return "consumer-" + InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "consumer-unknown";
        }
    }

    private void shutdownExecutor(ThreadPoolExecutor executor, String name, long awaitSeconds) {
        if (Objects.isNull(executor) || executor.isShutdown())
            return;

        try {
            executor.shutdown();
            if (!executor.awaitTermination(awaitSeconds, TimeUnit.SECONDS)) {
                List<Runnable> dropped = executor.shutdownNow();
                if (!dropped.isEmpty())
                    log.warn("[Broadcast] {} dropped {} tasks during forced shutdown", name, dropped.size());
                if (!executor.awaitTermination(3L, TimeUnit.SECONDS))
                    log.error("[Broadcast] {} did not terminate after forced shutdown", name);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[Broadcast] {} shutdown failed unexpectedly", name, e);
        }
    }
}
