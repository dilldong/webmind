package org.mind.framework.service.queue;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 队列统计信息
 * @version 1.0
 * @auther Marcus
 * @date 2025/5/27
 */
@Getter
@AllArgsConstructor
public class QueueStats {
    private final int queueSize;
    private final int remainingCapacity;
    private final int activeConsumers;
    private final boolean running;
    private final long totalProcessed;
    private final long totalFailed;
    private final double averageProcessingTime;

    @Override
    public String toString() {
        return String.format("QueueStats{queueSize=%d, remainingCapacity=%d, activeConsumers=%d, totalProcessed=%d, totalFailed=%d, avgProcessingTime=%.2fms, running=%s}",
                queueSize, remainingCapacity, activeConsumers, totalFailed, totalFailed, averageProcessingTime, running);
    }
}
