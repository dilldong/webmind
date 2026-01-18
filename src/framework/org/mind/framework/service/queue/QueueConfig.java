package org.mind.framework.service.queue;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 队列配置类
 *
 * @version 1.0
 * @author Marcus
 * @date 2025/5/27
 */
@Data
@Accessors(chain = true)
public class QueueConfig {
    private int queueCapacity = 1_024;              // 队列容量
    private int minConsumerThreads = 1;             // 最小消费者线程数
    private int maxConsumerThreads = 4;             // 最大消费者线程数
    private long pollTimeoutMs = 1_500L;            // 轮询超时时间(毫秒)
    private long awaitShutdownSeconds = 15L;

    private boolean enableLogStatus = false;        // 是否启用监控日志输出
    private boolean enableDynamicAdjust = false;    // 是否启用动态调整

    private double highLoadThreshold = 0.8;         // 高负载阈值
    private double lowLoadThreshold = 0.2;          // 低负载阈值
    private int queueHighWaterMark = 800;           // 队列高水位
    private int queueLowWaterMark = 200;            // 队列低水位

    private int maxDepth = 3;                       // 嵌套任务深度

    public QueueConfig adjustThresholds(double high, double low) {
        this.highLoadThreshold = high;
        this.lowLoadThreshold = low;
        return this;
    }

    public QueueConfig queueWaterMarks(int high, int low) {
        this.queueHighWaterMark = high;
        this.queueLowWaterMark = low;
        return this;
    }
}
