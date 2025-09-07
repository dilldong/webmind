package org.mind.framework.service.queue;

import org.mind.framework.util.DateUtils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 消费者性能指标
 * @version 1.0
 * @auther Marcus
 * @date 2025/5/27
 */
public class ConsumerMetrics {
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private volatile long lastActiveTime = DateUtils.CachedTime.currentMillis();

    public void recordSuccess(long processingTime) {
        processedCount.incrementAndGet();
        totalProcessingTime.addAndGet(processingTime);
        lastActiveTime = DateUtils.CachedTime.currentMillis();
    }

    public void recordFailure() {
        failedCount.incrementAndGet();
        lastActiveTime = DateUtils.CachedTime.currentMillis();
    }

    public double getAverageProcessingTime() {
        long processed = processedCount.get();
        return processed > 0 ? (double) totalProcessingTime.get() / processed : 0;
    }

    public long getProcessedCount() {
        return processedCount.get();
    }

    public long getFailedCount() {
        return failedCount.get();
    }

    public long getLastActiveTime() {
        return lastActiveTime;
    }
}
