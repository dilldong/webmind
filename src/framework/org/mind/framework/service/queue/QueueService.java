package org.mind.framework.service.queue;

import org.mind.framework.container.Destroyable;

/**
 * 轻量级的消息队列接口
 * 
 * @author dongping
 * @date Jun 11, 2012
 */
public interface QueueService extends Destroyable {

	/**
	 * 加入一个消息处理对象
	 * @param message
	 * @throws InterruptedException
	 * @author dongping
	 */
	void producer(DelegateMessage message);

	/**
	 * 阻塞式处理一个队列中的消息对象
	 * @return
	 * @throws InterruptedException
	 * @author dongping
	 */
	DelegateMessage consumer() throws InterruptedException;
}
