package org.mind.framework.service;

/**
 * 服务接口
 *
 * @since 2011.06
 * @author dp
 */
public interface Service {

	String SVC_REPLICA_NAME = "svc.replica";

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

	String getServiceName();
}
