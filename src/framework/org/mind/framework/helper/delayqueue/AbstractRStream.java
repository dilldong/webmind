package org.mind.framework.helper.delayqueue;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ThreadUtils;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.service.Updatable;
import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.web.Destroyable;
import org.redisson.api.AutoClaimResult;
import org.redisson.api.PendingEntry;
import org.redisson.api.RStream;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamPendingRangeArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.RedisBusyException;
import org.redisson.client.codec.StringCodec;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author: Marcus
 * @date: 2026/5/7
 * @version: 1.0
 */
@Getter
@Slf4j
public abstract class AbstractRStream implements Destroyable {
    private static final int BATCH_SIZE = 50;
    private static final Duration READ_BLOCK_MS = Duration.ofMillis(500L);

    /**
     * PEL 消息超过此空闲时长（ms）后触发 autoClaim 重投
     */
    private static final long PEL_IDLE_THRESHOLD_MS = 30_000L;

    /**
     * 同一 JVM 实例内的 consumer 名称，用于 Consumer Group 竞争消费
     * 生成逻辑：环境变量 > hostname > fallback。
     */
    protected String consumerName;

    protected String consumerGroup;

    private final String streamKey;

    private final String listenThreadPrefix;

    protected final RStream<String, String> rStream;

    /**
     * Stream consumer 线程池（单线程）
     */
    @Setter
    protected volatile ThreadPoolExecutor listenerExecutor;

    protected volatile boolean running;

    public abstract void init();
    protected abstract void handleMessage(StreamMessageId msgId, Map<String, String> fields);

    public AbstractRStream(String consumerGroup, String streamKey, String listenThreadPrefix) {
        this.consumerGroup = consumerGroup;
        this.streamKey = streamKey;
        this.listenThreadPrefix = listenThreadPrefix;
        this.consumerName = buildConsumerName();

        this.rStream = RedissonHelper.getClient().getStream(streamKey, StringCodec.INSTANCE);
        RedissonHelper.getInstance().addShutdownEvent(c -> destroy());
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
    protected void startStreamConsumer() {
        while (running) {
            try {
                // 1. PEL 重投（处理上次执行未 ACK 的消息）
                reclaimStalePendingMessages();

                // 2. 读取新消息（从未投递的消息）
                Map<StreamMessageId, Map<String, String>> messages =
                        rStream.readGroup(
                                consumerGroup, consumerName,
                                StreamReadGroupArgs.neverDelivered()
                                        .count(BATCH_SIZE)
                                        .timeout(READ_BLOCK_MS));

                if (Objects.isNull(messages) || messages.isEmpty())
                    continue;

                for (Map.Entry<StreamMessageId, Map<String, String>> entry : messages.entrySet()) {
                    handleMessage(entry.getKey(), entry.getValue());
                }

            } catch (Exception e) {
                log.error("Stream consumer error, stream: {} - {}", streamKey, e.getMessage(), e);
                ThreadUtils.sleepQuietly(Duration.ofSeconds(1L));
            }
        }

        log.info("Stream consumer stopped, stream: {}", streamKey);
    }

    /**
     * 扫描 PEL（Pending Entry List），autoClaim 空闲超过 {@code PEL_IDLE_THRESHOLD_MS} 的消息并重新处理。
     *
     * <p>覆盖场景：
     * <ul>
     *   <li>任务分发到 handleMessage 后 JVM 崩溃，导致 ACK 未发出</li>
     *   <li>业务回调持续失败超过阈值时间</li>
     * </ul>
     */
    protected void reclaimStalePendingMessages() {
        try {
            AutoClaimResult<String, String> result = rStream.autoClaim(
                    consumerGroup, consumerName,
                    PEL_IDLE_THRESHOLD_MS, TimeUnit.MILLISECONDS,
                    StreamMessageId.MIN,
                    BATCH_SIZE);

            if (Objects.isNull(result) || Objects.isNull(result.getMessages()) || result.getMessages().isEmpty())
                return;

            log.info("Reclaimed {} stale pending messages in stream: {}", result.getMessages().size(), streamKey);

            for (Map.Entry<StreamMessageId, Map<String, String>> entry : result.getMessages().entrySet()) {
                handleMessage(entry.getKey(), entry.getValue());
            }

        } catch (Exception e) {
            // autoClaim 非关键路径, 失败时静默忽略, 等待下次循环重试
            log.warn("autoClaim skipped: {}", e.getMessage());
        }
    }

    /**
     * 启动时恢复本实例自己遗留在 PEL 中的消息。
     *
     * <p>场景：服务崩溃导致 ACK 未发出，下次启动时用 idle=0 强制认领并重处理。
     * <p>安全性：仅操作本 ConsumerGroup 内、本 consumerName 名下的消息，不影响其他实例。
     * <p><b>前提</b>：consumerName 必须是稳定值（不含随机后缀），与 DelayQueueService 一致。
     */
    protected void recoverOwnPendingMessages() {
        try {
            List<PendingEntry> pending = rStream.listPending(
                    StreamPendingRangeArgs.groupName(consumerGroup)
                            .startId(StreamMessageId.MIN)
                            .endId(StreamMessageId.MAX)
                            .count(BATCH_SIZE)
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
                    rStream.claim(consumerGroup, consumerName, 0L, TimeUnit.MILLISECONDS, ids);

            if (Objects.isNull(messages) || messages.isEmpty())
                return;

            for (Map.Entry<StreamMessageId, Map<String, String>> entry : messages.entrySet()) {
                handleMessage(entry.getKey(), entry.getValue());
            }

        } catch (Exception e) {
            log.warn("Startup PEL recovery failed: {}", e.getMessage());
        }
    }

    /**
     * consumerName 生成逻辑：
     * 环境变量（APP_INSTANCE_ID）> hostname > fallback。
     * 保持稳定，重启后 PEL 才能正确恢复。
     */
    protected static String buildConsumerName() {
        String instanceId = Updatable.getAppInstanceId();
        if (StringUtils.isNotBlank(instanceId))
            return instanceId;

        try {
            return "consumer-" + InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "consumer-unknown";
        }
    }

    /**
     * 确保 Consumer Group 存在（第一次使用时创建，已存在时幂等忽略）。
     */
    protected void ensureConsumerGroupExists(StreamMessageId startStreamId) {
        try {
            rStream.createGroup(
                    StreamCreateGroupArgs.name(consumerGroup)
                            .id(startStreamId)// 开始消费位置
                            .makeStream());  // 若 Stream 不存在则自动创建（MKSTREAM 标志）
            log.info("Consumer group '{}' created for stream: {}", consumerGroup, streamKey);
        } catch (RedisBusyException e) {
            // BUSYGROUP 是正常情况（多实例启动 or 重启），静默降级
            log.info("Consumer group '{}' already exists, skipping", consumerGroup);
        } catch (Exception e) {
            log.warn("Consumer group init failed for stream: {}, error: {}", streamKey, e.getMessage());
        }
    }

    /**
     * DCL 确保消费线程只启动一次
     */
    protected void ensureRunning() {
        if (running)
            return;

        synchronized (this) {
            if (running)
                return;

            running = true;

            getListenerExecutor().execute(() -> {
                try {
                    // 启动时先恢复本实例遗留在 PEL 中的消息
                    recoverOwnPendingMessages();
                    startStreamConsumer();
                } catch (Exception e) {
                    log.error("RStream consumer thread crashed unexpectedly", e);
                    running = false;
                }
            });

            log.info("RStream started, stream: {}, consumerGroup: {}", streamKey, consumerGroup);
        }
    }

    public ThreadPoolExecutor getListenerExecutor() {
        if (Objects.isNull(listenerExecutor))
            initListenerExecutor();

        return listenerExecutor;
    }

    // DCL
    protected void initListenerExecutor() {
        if (Objects.isNull(listenerExecutor)) {
            synchronized (this) {
                if (Objects.isNull(listenerExecutor)) {
                    listenerExecutor = ExecutorFactory.newThreadPoolExecutor(
                            1, 1,
                            60L, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(2),
                            ExecutorFactory.newThreadFactory(listenThreadPrefix, true),
                            new ThreadPoolExecutor.CallerRunsPolicy());
                }
            }
        }
    }

    protected void ackSilently(StreamMessageId msgId) {
        try {
            // 1. 先 ACK（从 PEL 移除）
            rStream.ack(consumerGroup, msgId);

            // 2. 再 XDEL（从 Stream 主数据删除）
            rStream.remove(msgId);
        } catch (Exception e) {
            log.warn("Failed to ACK message {}: {}", msgId, e.getMessage());
        }
    }

    protected void shutdownExecutor(ExecutorService executor, String name, long awaitSeconds) {
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
