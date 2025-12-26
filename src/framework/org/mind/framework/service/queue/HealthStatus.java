package org.mind.framework.service.queue;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 健康状态类
 * @version 1.0
 * @author Marcus
 * @date 2025/6/2
 */
@Getter
@AllArgsConstructor
public class HealthStatus {
    private final boolean healthy;
    private final long timeSinceLastCheck;
    private final int consecutiveFailures;
    private final int activeConsumers;
    private final int poolSize;

    @Override
    public String toString() {
        return String.format("HealthStatus{healthy=%s, timeSinceLastCheck=%d, consecutiveFailures=%d, activeConsumers=%d, poolSize=%d}",
                healthy, timeSinceLastCheck, consecutiveFailures, activeConsumers, poolSize);
    }
}
