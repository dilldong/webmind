package org.mind.framework.service.queue;

import org.mind.framework.service.threads.ThreadContextPropagator;

import java.util.concurrent.TimeUnit;

/**
 * 本地轻量级消息队列
 *
 * @author dp
 * @version 2.0
 * @date Jun 11, 2012
 */
public class QueueLittle extends LightweightQueueService implements QueueService {

    public QueueLittle() {
        super();
    }

    public QueueLittle(QueueConfig config) {
        super(config);
    }

    @Override
    public boolean producer(DelegateMessage message) {
        return offer(ThreadContextPropagator.wrap(message));
    }

    @Override
    public boolean producer(DelegateMessage message, long timeout, TimeUnit unit) throws InterruptedException {
        return offer(ThreadContextPropagator.wrap(message), timeout, unit);
    }

    @Override
    public void destroy() {
        super.stop();
    }
}
