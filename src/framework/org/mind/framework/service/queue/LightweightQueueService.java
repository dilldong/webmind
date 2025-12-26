package org.mind.framework.service.queue;

import lombok.extern.slf4j.Slf4j;
import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.util.DateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量级队列服务
 * 支持并发安全的生产和消费，可配置多线程消费任务
 * 核心线程监控和自动恢复功能
 *
 * @version 1.0
 * @author Marcus
 * @date 2025/5/26
 */
@Slf4j(topic = "QueueService")
public class LightweightQueueService {

    // 存储嵌套队列
    private final ThreadLocal<Boolean> isWorkerThread = new ThreadLocal<>();
    private final ThreadLocal<NestedTaskContext> nestedContext;

    // 内部队列, 线程安全
    private final BlockingQueue<DelegateMessage> workerQueue;

    // 消费者线程池
    private final ThreadPoolExecutor consumerExecutor;

    // 服务状态
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 活跃消费者数量
    private final AtomicInteger activeConsumers = new AtomicInteger(0);

    // 配置参数
    private final QueueConfig config;

    // 统计信息
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final Map<String, ConsumerMetrics> consumerMetrics = new ConcurrentHashMap<>();

    // 核心线程监控相关
    private final AtomicLong lastHealthCheckTime = new AtomicLong(DateUtils.CachedTime.currentMillis());
    private final AtomicInteger consecutiveHealthCheckFailures = new AtomicInteger(0);
    private volatile Thread monitorThread;

    /**
     * 使用默认配置构造队列服务
     */
    public LightweightQueueService() {
        this(new QueueConfig());
    }

    /**
     * 使用指定配置构造队列服务
     */
    public LightweightQueueService(QueueConfig config) {
        this.config = config;
        this.workerQueue = new LinkedBlockingQueue<>(config.getQueueCapacity());

        this.consumerExecutor = ExecutorFactory.newThreadPoolExecutor(
                Math.max(1, config.getMinConsumerThreads()),
                Math.max(1, config.getMaxConsumerThreads()),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                ExecutorFactory.newThreadFactory("loop-queue-", false),
                new RejectedExecutionHandler()
        );

        // 核心线程不超时，保证服务稳定性
        this.consumerExecutor.allowCoreThreadTimeOut(false);

        // 初始嵌套任务处理
        this.nestedContext = ThreadLocal.withInitial(() -> new NestedTaskContext(config.getMaxDepth()));
    }

    /**
     * 启动队列服务
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            // 启动消费者线程
            for (int i = 0; i < config.getMinConsumerThreads(); ++i) {
                startConsumerThread();
            }

            // 启动监控线程
            if (Objects.isNull(monitorThread) || !monitorThread.isAlive())
                startMonitoringThread();

        } else {
            log.warn("Queue service is already running");
        }
    }

    /**
     * 启动单个消费者线程
     */
    private void startConsumerThread() {
        consumerExecutor.execute(new ConsumerTask());
    }

    /**
     * 停止队列服务
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping queue service....");

            try {
                // 停止监控线程
                if (Objects.nonNull(monitorThread) && monitorThread.isAlive())
                    monitorThread.interrupt();

                // 优雅关闭线程池
                consumerExecutor.shutdown();
                if (!consumerExecutor.awaitTermination(config.getAwaitShutdownSeconds(), TimeUnit.SECONDS)) {
                    log.warn("Consumer threads did not terminate gracefully, forcing shutdown....");
                    consumerExecutor.shutdownNow();
                }

                // 打印最终统计
                logStatistics();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                consumerExecutor.shutdownNow();
            }
        }
    }

    /**
     * 向队列添加元素（非阻塞）
     *
     * @param item 要添加的元素
     * @return true如果添加成功，false如果队列已满
     */
    public boolean offer(DelegateMessage item) {
        if (!isRunning())
            throw new IllegalStateException("Queue service is not running");

        if (Objects.isNull(item))
            throw new IllegalArgumentException("Item cannot be null");

        // 处理嵌套提交任务
        if (Boolean.TRUE.equals(isWorkerThread.get())) {
            NestedTaskContext context = nestedContext.get();
            if (Objects.nonNull(context))
                return context.addNestedTask(item);
        }

        boolean added = workerQueue.offer(item);
        if (!added)
            log.warn("Failed to add item to queue - queue is full");

        return added;
    }

    /**
     * 向队列添加元素（带超时）
     *
     * @param item    要添加的元素
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return true如果添加成功，false如果超时
     * @throws InterruptedException 如果等待时被中断
     */
    public boolean offer(DelegateMessage item, long timeout, TimeUnit unit) throws InterruptedException {
        if (!isRunning())
            throw new IllegalStateException("Queue service is not running");

        if (Objects.isNull(item))
            throw new IllegalArgumentException("Item cannot be null");

        // 处理嵌套提交任务
        if (Boolean.TRUE.equals(isWorkerThread.get())) {
            NestedTaskContext context = nestedContext.get();
            if (Objects.nonNull(context))
                return context.addNestedTask(item);
        }

        return workerQueue.offer(item, timeout, unit);
    }

    /**
     * 向队列添加元素（阻塞）
     *
     * @param item 要添加的元素
     * @throws InterruptedException 如果等待时被中断
     */
    public void put(DelegateMessage item) throws InterruptedException {
        if (!isRunning())
            throw new IllegalStateException("Queue service is not running");

        if (Objects.isNull(item))
            throw new IllegalArgumentException("Item cannot be null");

        // 处理嵌套提交任务
        if (Boolean.TRUE.equals(isWorkerThread.get())) {
            NestedTaskContext context = nestedContext.get();
            if (Objects.nonNull(context)) {
                context.addNestedTask(item);
                return;
            }
        }

        workerQueue.put(item);
    }

    /**
     * 获取队列当前大小
     */
    public int size() {
        return workerQueue.size();
    }

    /**
     * 检查队列是否为空
     */
    public boolean isEmpty() {
        return workerQueue.isEmpty();
    }

    /**
     * 获取剩余容量
     */
    public int remainingCapacity() {
        return workerQueue.remainingCapacity();
    }

    /**
     * 获取活跃消费者数量
     */
    public int getActiveConsumers() {
        return activeConsumers.get();
    }

    /**
     * 检查服务是否运行中
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取健康状态
     */
    public HealthStatus getHealthState() {
        long timeSinceLastCheck = DateUtils.CachedTime.currentMillis() - lastHealthCheckTime.get();
        boolean healthy = isRunning() &&
                !consumerExecutor.isShutdown() &&
                getActiveConsumers() >= config.getMinConsumerThreads() / 2 &&
                timeSinceLastCheck < 30_000L; // 30秒内有健康检查

        return new HealthStatus(
                healthy,
                timeSinceLastCheck,
                consecutiveHealthCheckFailures.get(),
                getActiveConsumers(),
                consumerExecutor.getPoolSize()
        );
    }

    /**
     * 获取队列统计信息
     */
    public QueueStats getStatisticState() {
        double avgProcessingTime = 0;
        long totalProcessed = this.totalProcessed.get();
        long totalFailed = this.totalFailed.get();

        if (!consumerMetrics.isEmpty()) {
            avgProcessingTime = consumerMetrics.values().stream()
                    .mapToDouble(ConsumerMetrics::getAverageProcessingTime)
                    .average()
                    .orElse(0);
        }

        return new QueueStats(
                size(),
                remainingCapacity(),
                getActiveConsumers(),
                isRunning(),
                totalProcessed,
                totalFailed,
                avgProcessingTime);
    }

    /**
     * 启动监控线程
     */
    private void startMonitoringThread() {
        this.monitorThread = ExecutorFactory.newDaemonThread("queue-monitor", () -> {
            while (isRunning()) {
                try {
                    // 每15秒检查一次
                    TimeUnit.SECONDS.sleep(15L);
                    // Health
                    performHealthCheck();
                    // Dynamic adjust thread
                    adjustConsumerThreads();
                    // Logs
                    logStatistics();

                    // 重置连续失败计数
                    consecutiveHealthCheckFailures.set(0);
                } catch (InterruptedException e) {
                    log.warn("Queue monitor thread interrupted: {}", Thread.currentThread().getName());
                    Thread.currentThread().interrupt();
                    break;
                } catch (Throwable e) {
                    log.error("Error in monitor thread", e);
                    consecutiveHealthCheckFailures.incrementAndGet();

                    // 如果连续失败次数过多，尝试重启监控
                    if (consecutiveHealthCheckFailures.get() > 3) {
                        log.error("Queue monitor thread has failed [{}] consecutively, may need manual intervention",
                                consecutiveHealthCheckFailures.get());
                    }
                }
            }

            log.debug("Queue monitor thread stopped: {}", Thread.currentThread().getName());
        });
        this.monitorThread.start();
    }

    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        // 检查线程池状态
        if (consumerExecutor.isShutdown() && isRunning()) {
            log.error("CRITICAL: Consumer executor is shutdown while service is running!");
            // 这里可以触发报警或尝试重建线程池
            return;
        }

        // record check time
        lastHealthCheckTime.set(DateUtils.CachedTime.currentMillis());

        // 检查活跃消费者数量
        int activeCount = getActiveConsumers();
        int poolSize = consumerExecutor.getPoolSize();
        int coreSize = consumerExecutor.getCorePoolSize();

        log.debug("Health check - Active: {}, Pool size: {}, Core size: {}",
                activeCount, poolSize, coreSize);

        // 如果活跃消费者数量远低于核心线程数，可能存在问题
        if (activeCount < coreSize / 2 && poolSize < coreSize) {
            log.warn("Potential thread loss detected - Active: {}, Pool: {}, Core: {}",
                    activeCount, poolSize, coreSize);

            // 尝试补充线程
            recoverCoreThreads();
        }

        // 检查队列是否有积压但没有消费者处理
        if (size() > 0 && activeCount == 0) {
            log.warn("CRITICAL: Queue has {} items but no active consumers!", size());
            // 尝试补充线程
            recoverCoreThreads();
        }
    }

    /**
     * 恢复核心线程
     */
    private void recoverCoreThreads() {
        try {
            int currentActive = getActiveConsumers();
            int targetCore = config.getMinConsumerThreads();

            if (currentActive < targetCore) {
                int threadsToAdd = targetCore - currentActive;
                log.info("Attempting to recover {} core consumer threads", threadsToAdd);

                for (int i = 0; i < threadsToAdd; ++i) {
                    startConsumerThread();
                }

                // 给新线程一些时间启动
                TimeUnit.SECONDS.sleep(2L);

                // 验证恢复结果
                int newActiveCount = getActiveConsumers();
                if (newActiveCount > currentActive) {
                    log.info("Successfully recovered {} consumer threads (from {} to {})",
                            newActiveCount - currentActive, currentActive, newActiveCount);
                } else {
                    log.error("Failed to recover consumer threads, may need manual intervention");
                }
            }
        } catch (Exception e) {
            log.error("Error during core thread recovery", e);
        }
    }

    /**
     * 记录统计信息
     */
    private void logStatistics() throws InterruptedException {
        if (!config.isEnableLogStatus())
            return;

        QueueStats stats = getStatisticState();
        log.info(String.format(
                "Queue Stats - Size: %d, Consumers: %d, Processed: %d, Failed: %d, Avg: %.2fms",
                stats.getQueueSize(), stats.getActiveConsumers(), stats.getTotalProcessed(),
                stats.getTotalFailed(), stats.getAverageProcessingTime()
        ));
    }

    /**
     * 动态调整消费者线程数
     */
    private void adjustConsumerThreads() {
        if (!config.isEnableDynamicAdjust() || !isRunning())
            return;

        int currentThreads = this.getActiveConsumers();
        int queueSize = this.size();

        // 基于队列大小和负载情况决定是否调整
        boolean shouldIncrease =
                currentThreads < config.getMinConsumerThreads() ||
                        (queueSize > config.getQueueHighWaterMark() &&
                                currentThreads < config.getMaxConsumerThreads());

        boolean shouldDecrease = (queueSize < config.getQueueLowWaterMark() &&
                currentThreads > config.getMinConsumerThreads());

        if (shouldIncrease) {
            log.info("High queue load detected ({}), adding consumer thread", queueSize);
            startConsumerThread();
        } else if (shouldDecrease) {
            log.info("Low queue load detected ({})", queueSize);
            // 不主动减少线程，让它们自然超时（通过调整核心线程数）
            int newCoreSize = Math.max(config.getMinConsumerThreads(), currentThreads - 1);
            consumerExecutor.setCorePoolSize(newCoreSize);
        }
    }

    /**
     * 消费者任务
     */
    private class ConsumerTask implements Runnable {
        @Override
        public void run() {
            String threadName = Thread.currentThread().getName();
            activeConsumers.incrementAndGet();
            ConsumerMetrics metrics = new ConsumerMetrics();
            consumerMetrics.put(threadName, metrics);
            // 标记为工作线程
            isWorkerThread.set(true);

            // 获取嵌套对象
            NestedTaskContext context = nestedContext.get();

            try {
                while (isRunning()) {
                    try {
                        // 获取元素，带超时
                        DelegateMessage item = workerQueue.poll(config.getPollTimeoutMs(), TimeUnit.MILLISECONDS);

                        if (Objects.nonNull(item)) {
                            try {
                                processTask(item, metrics, context);
                            } finally {
                                context.cleanup();
                            }
                        }

                    } catch (InterruptedException e) {
                        log.warn("Consumer thread interrupted: {}", threadName);
                        Thread.currentThread().interrupt();
                        break;
                    } catch (OutOfMemoryError oom) {
                        log.error("Consumer thread encountered OOM: {}", threadName, oom);
                        // OOM情况下，线程可能无法继续工作，直接退出
                        break;
                    } catch (Throwable e) {
                        log.error("Unexpected error in consumer thread: {}", threadName, e);
                        // 其他异常，记录后继续处理
                    }
                }
            } finally {
                activeConsumers.decrementAndGet();
                consumerMetrics.remove(threadName);
                isWorkerThread.remove();
                nestedContext.remove();
                log.debug("Consumer thread stopped: {}", threadName);
            }
        }

        private void processTask(DelegateMessage item, ConsumerMetrics metrics, NestedTaskContext context) {
            long startTime = DateUtils.CachedTime.currentMillis();
            try {
                // 检查并增加深度
                if (!context.incrementDepth()) {
                    log.warn("Task rejected, max nesting depth [{}] exceeded", context.maxDepth);
                    totalFailed.incrementAndGet();
                    return;
                }

                // 执行主任务
                item.process();

                // 处理本层的嵌套任务
                Queue<DelegateMessage> currentLevelTasks = context.getCurrentLevelTasks();
                if (Objects.nonNull(currentLevelTasks)) {
                    DelegateMessage nestedTask;
                    while (Objects.nonNull(nestedTask = currentLevelTasks.poll())) {
                        processTask(nestedTask, metrics, context); // 递归处理
                    }
                }

                long processingTime = DateUtils.CachedTime.currentMillis() - startTime;
                metrics.recordSuccess(processingTime);
                totalProcessed.incrementAndGet();
            } catch (Exception e) {
                metrics.recordFailure();
                totalFailed.incrementAndGet();
                log.error("Error processing task: {}", item, e);
                // 这里可以根据需要添加错误处理逻辑，比如重试、死信队列等
            } finally {
                // 减少深度
                context.decrementDepth();
            }
        }
    }

    // 嵌套任务对象
    private static class NestedTaskContext {
        private final AtomicInteger depth = new AtomicInteger(0);

        // 非阻塞 的并发队列
        private final List<Queue<DelegateMessage>> taskLevels = new ArrayList<>();

        // 最大深度限制
        private final int maxDepth;

        public NestedTaskContext(int maxDepth) {
            this.maxDepth = maxDepth;

            // 预分配队列以减少运行时分配
            for (int i = 0; i < maxDepth; ++i) {
                taskLevels.add(new ConcurrentLinkedQueue<>());
            }
        }

        public boolean incrementDepth() {
            if (depth.incrementAndGet() > maxDepth) {
                depth.decrementAndGet();
                return false;
            }
            return true;
        }

        public void decrementDepth() {
            if (getDepth() > 0)
                depth.decrementAndGet();
        }

        public boolean addNestedTask(DelegateMessage task) {
            // 是否还能添加更深层的任务
            if (getDepth() > maxDepth) {
                log.warn("Nested task rejected - max depth {} exceeded", maxDepth);
                return false;
            }

            int currentDepth = getDepth() - 1;
            if (currentDepth < taskLevels.size()) {
                taskLevels.get(currentDepth).offer(task);
                return true;
            }
            return false;
        }

        public Queue<DelegateMessage> getCurrentLevelTasks() {
            int currentDepth = getDepth();
            if (currentDepth > 0 && currentDepth <= taskLevels.size()) {
                // 返回当前深度-1的队列（因为任务是在incrementDepth之前添加的）
                return taskLevels.get(currentDepth - 1);
            }
            return null;
        }

        public int getDepth() {
            return depth.get();
        }

        public void cleanup() {
            // 清理所有队列中的任务
            for (Queue<DelegateMessage> queue : taskLevels) {
                queue.clear();
            }
            depth.set(0);
        }
    }

}