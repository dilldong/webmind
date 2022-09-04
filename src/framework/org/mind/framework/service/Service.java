package org.mind.framework.service;

/**
 * 服务接口
 * 
 * @author dp
 */
public interface Service {

	/**
	 * 开始服务
	 * 
	 * @author dp
	 */
	void start();
	
	/**
	 * 停止服务
	 * 
	 * @author dp
	 */
	void stop();
	
	boolean isStart();
	
	boolean isStop();
	
	int getServiceState();
}
