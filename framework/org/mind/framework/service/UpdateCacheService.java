package org.mind.framework.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.log4j.Logger;

/**
 * 从数据库定时同步数据
 * 
 * @author dongping
 */
public class UpdateCacheService extends LoopWorkerService {

	static Logger logger = Logger.getLogger(UpdateCacheService.class);
	
	private List<Updateable> updaters;

	@Override
	protected void doLoopWork() {
		if (updaters != null && !updaters.isEmpty()) {
			for (Updateable update : updaters) {
				if (update != null) {
					try {
						update.doUpate();
					} catch (Exception e) {
						logger.error(e.getMessage(), e);
					}
				}
			}
		}
		System.gc();
	}

	public List<Updateable> getUpdaters() {
		return updaters;
	}

	public void setUpdaters(List<Updateable> updaters) {
		this.updaters = updaters;
	}

	public void addUpdater(Updateable updater) {
		if (updaters == null) {
			updaters = new CopyOnWriteArrayList<Updateable>();
		}
		
		if (updater != null && !updaters.contains(updater)) {
			updaters.add(updater);
		}
	}

	public boolean removeUpdater(Updateable updater) {
		if (updaters == null) {
			updaters = new CopyOnWriteArrayList<Updateable>();
		}
		return updaters.remove(updater);
	}
}
