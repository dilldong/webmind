package org.mind.framework.service.queue;

import org.mind.framework.service.Updateable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsumerService implements Updateable {

    private static final Logger log = LoggerFactory.getLogger(ConsumerService.class);

    private QueueService queueService;

    @Override
    public void doUpate() {
        if (log.isInfoEnabled())
            log.info("Listening queue message....");
        try {
            DelegateMessage delegate = queueService.consumer();
            try {
                delegate.process();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    public void setQueueService(QueueService queueService) {
        this.queueService = queueService;
    }

}
