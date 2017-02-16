package org.mind.framework.service.queue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mind.framework.service.Updateable;

public class ConsumerService implements Updateable {

	private static final Log log = LogFactory.getLog(ConsumerService.class);
	
	private QueueService queueService;

	@Override
	public void doUpate() {
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
