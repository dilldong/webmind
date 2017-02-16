package org.mind.framework.service;

import org.apache.log4j.Logger;

/**
 * 利用独立线程执行循环处理工作服务类
 * 
 * @author dongping
 */
public abstract class LoopWorkerService extends AbstractService {

	static Logger logger = Logger.getLogger(LoopWorkerService.class);

	private boolean isLoop = true;

	private Thread workerThread;

	/*sleep 时长*/
	private long spaceTime;

	private boolean daemon;

	public LoopWorkerService() {
		super();
	}

	public LoopWorkerService(long spaceTime) {
		super();
		this.spaceTime = spaceTime;
	}

	@Override
	public final void start() {
		serviceState = STATE_STARTED;
		prepareStart();
		
		if (workerThread == null) {
			workerThread = new Thread(new Worker(), this.serviceName);
			workerThread.setDaemon(this.daemon);
		}
		
		if (spaceTime <= 0) {
			logger.warn("The space time is(ms) " + spaceTime);
		}
		
		if (!workerThread.isAlive()) {
			workerThread.start();
		}
	}

	@Override
	public final void stop() {
		serviceState = STATE_STOPED;
		prepareStop();
		isLoop = false;
		
		if (workerThread != null)
			workerThread.interrupt();
	}

	protected void prepareStart() {

	}

	protected void prepareStop() {

	}

	/**
	 * 服务线程刚开始时调用的方法，若需做一些初始化操作可覆盖此方法来添加
	 */
	protected void toStart() {
		logger.info("service [" + serviceName + "] is to start ....");
	}

	/**
	 * 服务线程将要结束时调用的方法，若需做一些清理操作可覆盖此方法来添加
	 */
	protected void toEnd() {
		logger.info("service [" + serviceName + "] is to end ....");
	}

	@Override
	public boolean isStart() {
		return workerThread != null && workerThread.isAlive();
	}

	@Override
	public boolean isStop() {
		return isLoop;
	}

	/*
	 * 循环处理内容
	 */
	protected abstract void doLoopWork();

	private class Worker implements Runnable {
		public void run() {
			toStart();
			while (isLoop) {
				try {
					doLoopWork();
				} catch (Exception e) {
					logger.error(e.getMessage(), e);
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e1) {
						logger.error(e1.getMessage());
					}
				}
				
				if (spaceTime > 0){
					try {
						Thread.sleep(spaceTime);
					} catch (InterruptedException e) {
						logger.error(e.getMessage());
					}
				}else{ // 通过spaceTime等于0则跳出循环处理
					break;
				}
			}
			toEnd();
		}
	}

	public long getSpaceTime() {
		return spaceTime;
	}

	public void setSpaceTime(long spaceTime) {
		this.spaceTime = spaceTime;
	}

	public boolean isDaemon() {
		return daemon;
	}

	public void setDaemon(boolean on) {
		this.daemon = on;
		if (workerThread != null) {
			workerThread.setDaemon(on);
		}
	}

	protected void interruptSleep() {
		if (workerThread != null && workerThread.isAlive()) {
			workerThread.interrupt();
		}
	}
}
