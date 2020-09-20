package org.mind.framework.service.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

public final class QueueLittle implements QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueLittle.class);

    private BlockingQueue<DelegateMessage> queueInstance;

    private QueueLittle() {

    }

    public void setQueueInstance(BlockingQueue<DelegateMessage> queueInstance) {
        this.queueInstance = queueInstance;
    }

    @Override
    public void producer(DelegateMessage message) {
        try {
            queueInstance.put(message);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public DelegateMessage consumer() throws InterruptedException {
        log.info("current queue size: {}", queueInstance.size());
        DelegateMessage message = queueInstance.take();
        return message;
    }

    @Override
    public void destroy() {
        log.info("Destroy QueueLittle elements....");
        if (this.queueInstance == null || this.queueInstance.isEmpty())
            return;

        int size = this.queueInstance.size();
        log.info("QueueService size: {}", size);
        if (size > 0)
            this.queueInstance.clear();
    }
}
