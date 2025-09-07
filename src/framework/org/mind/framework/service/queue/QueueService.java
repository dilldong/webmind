package org.mind.framework.service.queue;

import org.mind.framework.web.Destroyable;

import java.util.concurrent.TimeUnit;

/**
 * 轻量级的消息队列接口
 *
 * @author dp
 * @version 2.0
 * @date Jun 11, 2012
 */
public interface QueueService extends Destroyable {

    /**
     * 加入一个消息对象至队列中
     *
     * @return true: add success, false: add failed
     */
    boolean producer(DelegateMessage message);

    /**
     * Add a message to queue, specify timeout
     *
     * @return true: add success, false: add failed
     */
    boolean producer(DelegateMessage message, long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * 检查队列是否为空
     */
    boolean isEmpty();

    /**
     * 获取队列当前大小
     */
    int size();

    /**
     * 获取剩余容量
     */
    int remainingCapacity();

    /**
     * 获取活跃消费者数量
     */
    int getActiveConsumers();

    /**
     * 检查服务是否运行中
     */
    boolean isRunning();

    /**
     * 获取队列统计
     */
    QueueStats getStatisticState();

    /**
     * 获取队列健康度
     */
    HealthStatus getHealthState();
}
