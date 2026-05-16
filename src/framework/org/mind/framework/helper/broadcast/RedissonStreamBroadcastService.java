package org.mind.framework.helper.broadcast;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.helper.delayqueue.AbstractRStream;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;

import java.util.Map;
import java.util.Objects;
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
public class RedissonStreamBroadcastService extends AbstractRStream {
    private static final String FIELD_PAYLOAD = "payload";

    /**
     * 业务处理器，由调用方通过 {@link #registerHandler} 注入。
     * payload = 发布时传入的字符串（如 cacheKey）。
     */
    private volatile Consumer<String> broadcastHandler;

    /**
     * Stream 最大保留条数（近似）, 超过后自动裁剪旧消息
     */
    @Setter
    @Getter
    private int maxStreamLen = 10_000;

    /**
     * @param baseName Redis key 前缀，如 {@code "cache:user"}。
     *                 实际 Stream key 为 {@code "{baseName}:broadcast:stream"}。
     */
    public RedissonStreamBroadcastService(String baseName, String consumerGroup) {
        super(consumerGroup, baseName + ":broadcast:stream", "broadcast-listen-");

        // 为每个实例创建不同的 consumerGroup
        super.consumerGroup += super.consumerName;

        // 尽早建立 ConsumerGroup（幂等），MKSTREAM 自动创建 Stream
        ensureConsumerGroupExists(StreamMessageId.NEWEST);
    }

    /**
     * 初始化线程池并启动消费循环
     */
    @Override
    public void init() {
        initListenerExecutor();
        ensureRunning();
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
            /*
             * 按间隔裁剪 Stream，使用近似 MAXLEN（~ 符号）性能更优;
             * 注意： noLimit() 保证的是“只要执行裁剪，就尽可能删干净”，但它不抵消 trimNonStrict() 的“近似”特性。
             */
            rStream.add(StreamAddArgs.entry(FIELD_PAYLOAD, payload)
                    .trimNonStrict()
                    .maxLen(maxStreamLen)
                    .noLimit());
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
        log.info("[Broadcast] Listener stopped, stream: {}", getStreamKey());
    }

    /**
     * 处理单条广播消息。
     *
     * <p>业务成功 → ACK + XDEL；失败 → 不 ACK（留在 PEL，重启后 {@link #recoverOwnPendingMessages()} 重投）。
     */
    @Override
    protected void handleMessage(StreamMessageId msgId, Map<String, String> fields) {
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
     * <b>注意</b>：广播模式不使用 autoClaim，因为每个实例独享自己的 ConsumerGroup，
     * 不存在其他实例可以帮助"抢救"的情况。恢复依赖启动时的 {@link #recoverOwnPendingMessages()}。
     */
    @Override
    protected void reclaimStalePendingMessages(){
        // do nothing
    }

    @Override
    protected void ackSilently(StreamMessageId msgId) {
        try {
            // ACK（从 PEL 移除）
            rStream.ack(consumerGroup, msgId);
        } catch (Exception e) {
            log.warn("[Broadcast] Failed to ACK message {}: {}", msgId, e.getMessage());
        }
    }

    @Override
    public void destroy() {
        stopListener();
        shutdownExecutor(listenerExecutor, "Broadcast-Graceful", 5L);
    }

}
