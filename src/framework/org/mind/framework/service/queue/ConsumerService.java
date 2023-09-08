package org.mind.framework.service.queue;

import org.mind.framework.service.Updatable;
import org.mind.framework.web.Destroyable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public abstract class ConsumerService implements Updatable, Destroyable {
    
    static final Logger log = LoggerFactory.getLogger(ConsumerService.class);

    protected final QueueService queueService;

    public ConsumerService(QueueService queueService) {
        this.queueService = queueService;
    }

    @Override
    public abstract void doUpdate();

    @Override
    public void destroy() {

    }

    protected void consumption() {
        try {
            DelegateMessage delegate = queueService.consumer();
            if (Objects.isNull(delegate))
                return;

            if (log.isDebugEnabled())
                log.debug("Consumer queue message....");

            try {
                delegate.process();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }
}