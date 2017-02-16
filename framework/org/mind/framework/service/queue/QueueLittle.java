package org.mind.framework.service.queue;

import java.util.concurrent.BlockingQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public final class QueueLittle implements QueueService {
	
	private static final Log log = LogFactory.getLog(QueueLittle.class);
	
	private BlockingQueue<DelegateMessage> queueInstance;
	
	private QueueLittle(){
		
	}
	
	public void setQueueInstance(BlockingQueue<DelegateMessage> queueInstance) {
		this.queueInstance = queueInstance;
	}
	
	@Override
	public void producer(DelegateMessage message){
		try {
			queueInstance.put(message);
		} catch (InterruptedException e) {
			log.error(e.getMessage(), e);
		}
	}
	
	@Override
	public DelegateMessage consumer() throws InterruptedException{
		log.info("current queue size:"+ queueInstance.size());
		DelegateMessage message = (DelegateMessage)queueInstance.take();
		return message;
	}
	
	@Override
	public void destroy(){
		log.info("Destroy QueueLittle elements....");
		if(this.queueInstance == null || this.queueInstance.isEmpty())
			return;
		
		int size = this.queueInstance.size();
		log.info("QueueService size:"+ size);
		if(size > 0)
			this.queueInstance.clear();
	}
}
