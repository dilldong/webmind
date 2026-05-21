package org.mind.framework.service;

/**
 * 服务接口
 *
 * @since 2011.06
 * @author dp
 */
public interface Service {

	String SVC_REPLICA_NAME = "svc.replica";

	String APP_INSTANCE_ID = "app.instance";

	String SVC_REPLICA_NAME_ENV = "SVC_REPLICA";

	String APP_INSTANCE_ID_ENV = "APP_INSTANCE_ID";

	/**
	 * 开启服务
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
	
	boolean isStarted();
	
	boolean isStop();
	
	int getServiceState();

	String getServiceName();
}
