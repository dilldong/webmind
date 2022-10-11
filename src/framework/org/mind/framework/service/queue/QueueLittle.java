package org.mind.framework.service.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;

public class QueueLittle implements QueueService {

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
        // BlockingQueue.take() is thread safety
        return queueInstance.take();
    }

    @Override
    public BlockingQueue<DelegateMessage> getQueue() {
        return queueInstance;
    }

    @Override
    public int size() {
        return Objects.isNull(queueInstance) ? 0 : queueInstance.size();
    }


    @Override
    public void destroy() {
        log.info("Destroy QueueLittle elements....");
        if (Objects.isNull(queueInstance) || queueInstance.isEmpty())
            return;

        int size = this.size();
        log.info("QueueService size: {}", size);
        if (size > 0)
            this.queueInstance.clear();
    }
}
