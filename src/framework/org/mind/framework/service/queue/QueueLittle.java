package org.mind.framework.service.queue;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;

public class QueueLittle implements QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    @Setter
    private BlockingQueue<DelegateMessage> queueInstance;

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
        // LinkedBlockingQueue is thread safety
        // current thread await: take()
        return queueInstance.poll();
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
    public synchronized void destroy() {
        if (Objects.isNull(queueInstance) || queueInstance.isEmpty())
            return;

        int size = this.size();
        log.info("Destroy QueueLittle@{} elements: [{}]", Integer.toHexString(hashCode()), size);
        if (size > 0)
            this.queueInstance.clear();
    }
}
