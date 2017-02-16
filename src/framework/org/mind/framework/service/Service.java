package org.mind.framework.service;

/**
 * 服务接口
 * 
 * @author dongping
 */
public interface Service {

	/**
	 * 开始服务
	 * 
	 * @author dongping
	 */
	void start();
	
	/**
	 * 停止服务
	 * 
	 * @author dongping
	 */
	void stop();
	
	boolean isStart();
	
	boolean isStop();
	
	int getServiceState();
}
