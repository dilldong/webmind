package org.mind.framework.service.queue;

import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class QueueLittle implements QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final Object queueObject = new Object();

    @Setter
    private BlockingQueue<DelegateMessage> workerQueue;

    @Setter
    private long awaitSeconds = 15L;

    @Setter
    private boolean waitTasksToCompleteOnShutdown = false;

    @Override
    public boolean producer(DelegateMessage message) {
        // LinkedBlockingQueue is thread safety
        // current thread await: put()
        return workerQueue.offer(message);
    }

    @Override
    public boolean producer(DelegateMessage message, long timeout, TimeUnit unit) throws InterruptedException {
        return workerQueue.offer(message, timeout, unit);
    }

    @Override
    public DelegateMessage consumer() throws InterruptedException {
        // LinkedBlockingQueue is thread safety
        // current thread await: take()
        return workerQueue.poll();
    }

    @Override
    public DelegateMessage consumer(long timeout, TimeUnit unit) throws InterruptedException {
        return workerQueue.poll(timeout, unit);
    }

    @Override
    public BlockingQueue<DelegateMessage> getQueue() {
        return workerQueue;
    }

    @Override
    public boolean isEmpty() {
        return Objects.isNull(workerQueue) || workerQueue.isEmpty();
    }

    @Override
    public int size() {
        return Objects.isNull(workerQueue) ? 0 : workerQueue.size();
    }

    @Override
    public void destroy() {
        if (Objects.isNull(workerQueue) || workerQueue.isEmpty())
            return;

        synchronized (queueObject) {
            // wait for tasks to complate
            if(waitTasksToCompleteOnShutdown && awaitSeconds > 0L){
                try {
                    TimeUnit.SECONDS.sleep(awaitSeconds);
                } catch (InterruptedException ignored) {}
            }

            int size = this.size();
            log.info("Destroy QueueLittle@{} elements: [{}]", Integer.toHexString(hashCode()), size);
            if (size > 0)
                this.workerQueue.clear();
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                .append("queueSize", this.size())
                .toString();
    }
}
