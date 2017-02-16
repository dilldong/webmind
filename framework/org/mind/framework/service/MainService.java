package org.mind.framework.service;

import java.util.Set;

import org.apache.log4j.Logger;

/**
 * 提供非WEB应用服务，不允许使用多个相同的对象构建服务
 * @author dp
 *
 */
public class MainService extends AbstractService {

	static Logger logger = Logger.getLogger(MainService.class);

	private Set<Service> childServices;

	public MainService() {
		setServiceName(getClass().getSimpleName());
	}

	protected void startChildServices() {
		if (childServices != null) {
			for (final Service serv : childServices) {
				if (serv != null) {
					Thread t = new Thread(new Runnable() {
						@Override
						public void run() {
							logger.info("Service " + serv + " to start ....");
							serv.start();
						}
					});
					t.start();
				}
			}
		}
	}

	protected void stopChildServices() {
		if (childServices != null) {
			for (final Service serv : childServices) {
				if (serv != null) {
					Thread t = new Thread(new Runnable() {
						public void run() {
							logger.info("Service " + serv + " to stop ....");
							serv.stop();
						}
					});

					t.start();
				}
			}
		}
	}

	public final void start() {
		startChildServices();
		serviceState = STATE_STARTED;
	}

	public final void stop() {
		stopChildServices();
		serviceState = STATE_STOPED;
	}

	public Set<Service> getChildServices() {
		return childServices;
	}

	public void setChildServices(Set<Service> childServices) {
		this.childServices = childServices;
	}

}
