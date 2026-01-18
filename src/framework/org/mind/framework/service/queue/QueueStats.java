package org.mind.framework.service.queue;

/**
 * 队列统计信息
 *
 * @author Marcus
 * @version 1.0
 * @date 2025/5/27
 */
public record QueueStats(int queueSize,
                         int remainingCapacity,
                         int activeConsumers,
                         boolean running,
                         long totalProcessed,
                         long totalFailed,
                         double averageProcessingTime) {
    @Override
    public String toString() {
        return String.format("QueueStats{queueSize=%d, remainingCapacity=%d, activeConsumers=%d, totalProcessed=%d, totalFailed=%d, avgProcessingTime=%.2fms, running=%s}",
                queueSize, remainingCapacity, activeConsumers, totalFailed, totalFailed, averageProcessingTime, running);
    }
}
