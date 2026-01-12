package org.mind.framework.service.queue;

/**
 * 健康状态类
 *
 * @author Marcus
 * @version 1.0
 * @date 2025/6/2
 */
public record HealthStatus(boolean healthy,
                           long timeSinceLastCheck,
                           int consecutiveFailures,
                           int activeConsumers,
                           int poolSize) {
    @Override
    public String toString() {
        return String.format("HealthStatus{healthy=%s, timeSinceLastCheck=%d, consecutiveFailures=%d, activeConsumers=%d, poolSize=%d}",
                healthy, timeSinceLastCheck, consecutiveFailures, activeConsumers, poolSize);
    }
}
