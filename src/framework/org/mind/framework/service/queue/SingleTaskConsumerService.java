package org.mind.framework.service.queue;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class SingleTaskConsumerService extends ConsumerService {

    public SingleTaskConsumerService(QueueService queueService) {
        super(queueService);
    }

    @Override
    public void doUpdate() {
        if (queueService.isEmpty())
            return;

        this.consumption();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                .append("queueService", queueService.toString())
                .toString();
    }
}