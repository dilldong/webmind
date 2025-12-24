package org.mind.framework.service.threads;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.mind.framework.util.DateUtils;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 根据任务负载动态调整线程池参数
 *
 * @version 1.0
 * @auther Marcus
 * @date 2025/5/26
 */
@Slf4j
public class DynamicThreadPoolExecutor extends ThreadPoolExecutor {
    private final AtomicLong taskCount = new AtomicLong(0);
    private final AtomicLong completedTaskCount = new AtomicLong(0);
    private volatile long lastAdjustTime = DateUtils.CachedTime.currentMillis();

    // 动态调整参数
    private static final long ADJUST_INTERVAL = 30_000;          // 30秒调整一次
    private static final int QUEUE_SIZE = 256;                   // 队列大小
    private static final int QUEUE_THRESHOLD = QUEUE_SIZE >> 1;  // 高负载阈值
    private static final double LOW_LOAD_THRESHOLD = 0.3;        // 低负载阈值

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    @Setter
    private boolean enableLogStatus;        // 是否启用日志报告
    @Setter
    private long logIntervalSeconds;        // 日志输出间隔时长(秒)

    public DynamicThreadPoolExecutor() {
        this(true, 10L);
    }

    public DynamicThreadPoolExecutor(boolean enableLogStatus, long logIntervalSeconds) {
        super(
                2,                         // 初始核心线程数稍微保守
                Math.max(8, CPU_COUNT << 2),          // 最大线程数更宽松
                120L,                                 // 更长的存活时间
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_SIZE),  // 较小的队列，促进线程创建
                ExecutorFactory.newThreadFactory("dynamic-task-", false),
                new CallerRunsPolicy()
        );

        this.allowCoreThreadTimeOut(true);

        // 是否启用输出日志报告
        this.enableLogStatus = enableLogStatus;
        this.logIntervalSeconds = logIntervalSeconds;

        // 启动监控和调整线程
        startMonitoringThread();
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        taskCount.incrementAndGet();
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        completedTaskCount.incrementAndGet();

        if (t != null)
            log.error("Task execution failed: {}", t.getMessage(), t);
    }

    /**
     * 动态调整线程池大小
     */
    private void adjustPoolSize() {
        long now = DateUtils.CachedTime.currentMillis();
        if (now - lastAdjustTime < ADJUST_INTERVAL)
            return;

        int currentCore = getCorePoolSize();
        int currentMax = getMaximumPoolSize();
        int activeCount = getActiveCount();
        int queueSize = getQueue().size();

        // 计算负载率
        double threadLoadRate = (double) activeCount / currentCore;
        double queueLoadRate = (double) queueSize / QUEUE_SIZE;

        // 计算新的核心线程数
        int newCoreSize = currentCore;
        int newMaxSize = currentMax;

        if ((threadLoadRate >= 1.0D && queueSize > QUEUE_THRESHOLD) || queueLoadRate > 0.7) {
            // 高负载：线程全部忙碌且队列积压较多，或队列使用率超过70%
            newCoreSize = Math.min(currentCore + 2, CPU_COUNT << 1);
            newMaxSize = Math.min(currentMax + 4, CPU_COUNT << 2);
            log.info("High load detected, increasing pool size: {}/{}", newCoreSize, newMaxSize);

        } else if (threadLoadRate < LOW_LOAD_THRESHOLD && queueSize == 0 && currentCore > 2) {
            // 低负载：线程使用率低且无队列积压
            newCoreSize = Math.max(currentCore - 1, 2);// 最少保持2个核心线程
            newMaxSize = Math.max(currentMax - 2, CPU_COUNT << 1);
            log.info("Low load detected, decreasing pool size: {}/{}", newCoreSize, newMaxSize);
        }

        // 应用新的大小
        if (newMaxSize != currentMax)
            setMaximumPoolSize(newMaxSize);

        if (newCoreSize != currentCore)
            setCorePoolSize(newCoreSize);

        lastAdjustTime = now;
    }

    /**
     * 启动监控线程
     */
    private void startMonitoringThread() {
        ExecutorFactory.newDaemonThread("dynamic-threads-monitor", () -> {
            while (!isShutdown()) {
                try {
                    // 每10秒检查一次
                    TimeUnit.SECONDS.sleep(10L);
                    adjustPoolSize();
                    logStatus();
                } catch (InterruptedException e) {
                    log.warn("Dynamic monitor thread interrupted: {}", Thread.currentThread().getName());
                    Thread.currentThread().interrupt();
                    break;
                } catch (Throwable e) {
                    log.error("Error in monitor thread", e);
                }
            }
        }).start();
    }

    /**
     * 打印线程池状态
     */
    public void logStatus() throws InterruptedException {
        if (!enableLogStatus)
            return;

        // log output interval
        long interval = this.logIntervalSeconds - 10L;
        if (interval > 0)
            TimeUnit.SECONDS.sleep(interval);

        log.info(
                String.format("Dynamic ThreadPool - Core: %d, Max: %d, Active: %d, Size: %d, Completed: %d, Total: %d",
                        getCorePoolSize(), getMaximumPoolSize(), getActiveCount(),
                        getQueue().size(), getCompletedTaskCount(), getTaskCount()
                )
        );
    }
}
