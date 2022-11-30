package org.mind.framework.service.queue;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;

public class QueueLittle implements QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final Object queueObject = new Object();

    @Setter
    private transient volatile BlockingQueue<DelegateMessage> queueInstance;

    @Override
    public boolean producer(DelegateMessage message) {
        // LinkedBlockingQueue is thread safety
        // current thread await: put()
        return queueInstance.offer(message);
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
    public void destroy() {
        synchronized (queueObject) {
            if (Objects.isNull(queueInstance) || queueInstance.isEmpty())
                return;

            int size = this.size();
            log.info("Destroy QueueLittle@{} elements: [{}]", Integer.toHexString(hashCode()), size);
            if (size > 0)
                this.queueInstance.clear();
        }
    }
}
