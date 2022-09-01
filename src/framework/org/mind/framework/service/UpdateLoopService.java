package org.mind.framework.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author dongping
 */
public class UpdateLoopService extends LoopWorkerService {

    static final Logger log = LoggerFactory.getLogger(UpdateLoopService.class);

    private List<Updateable> updaters;

    @Override
    protected void doLoopWork() {
        if (updaters != null && !updaters.isEmpty()) {
            updaters.forEach(updateable -> {
                if (updateable != null) {
                    try {
                        updateable.doUpate();
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }
            });
        }
    }

    public List<Updateable> getUpdaters() {
        return updaters;
    }

    public void setUpdaters(List<Updateable> updaters) {
        this.updaters = updaters;
    }

    public void addUpdater(Updateable updater) {
        if (updaters == null)
            updaters = new CopyOnWriteArrayList<>();

        updaters.add(updater);
    }

    public boolean removeUpdater(Updateable updater) {
        if (updaters == null) {
            updaters = new CopyOnWriteArrayList<>();
            return false;
        }

        return updaters.remove(updater);
    }
}
