package org.mind.framework.mail.service;

import org.mind.framework.mail.DefaultEmailSupport;
import org.mind.framework.mail.MailHead;
import org.mind.framework.service.queue.QueueService;

public class EmailServiceImpl implements EmailService {

	private QueueService queueService;
	
	@Override
	public void send(final MailHead head){
		this.queueService.producer(new DefaultEmailSupport(){{
			this.setFrom(head.getFrom());
			this.setTemplateName(head.getTemplateName());
			this.setAddress(head.getAddress());
			this.setModel(head.getBody());
			this.setSubject(head.getSubject());
			this.setMailType(head.getMailType());
		}});
	}

	public void setQueueService(QueueService queueService) {
		this.queueService = queueService;
	}
	
}
