package org.mind.framework.service.queue;

import org.mind.framework.container.Destroyable;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 轻量级的消息队列接口
 *
 * @author dp
 * @date Jun 11, 2012
 */
public interface QueueService extends Destroyable {

    /**
     * 加入一个消息对象至队列中
     *
     * @param message
     * @throws InterruptedException
     * @author dp
     */
    boolean producer(DelegateMessage message);

    /**
     * Add a message to queue, specify timeout
     *
     * @return true: add success, false: add failed
     * @throws InterruptedException
     */
    boolean producer(DelegateMessage message, long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * 消费队列中的消息对象
     *
     * @return
     * @throws InterruptedException
     * @author dp
     */
    DelegateMessage consumer() throws InterruptedException;

    /**
     * Message objects in the consumption queue, specify timeout
     *
     * @return
     * @throws InterruptedException
     */
    DelegateMessage consumer(long timeout, TimeUnit unit) throws InterruptedException;

    BlockingQueue<DelegateMessage> getQueue();

    boolean isEmpty();

    int size();
}
