package org.mind.framework.service;

import lombok.Getter;
import lombok.Setter;
import org.mind.framework.container.Destroyable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author dp
 */
public class UpdateLoopService extends LoopWorkerService {

    @Getter
    @Setter
    private List<Updatable> updaters;

    @Override
    protected void doLoopWork() {
        if (updaters != null && !updaters.isEmpty()) {
            updaters.forEach(updatable -> {
                if (Objects.nonNull(updatable))
                    updatable.doUpdate();
            });
        }
    }

    @Override
    protected void prepareStop() {
        if (Objects.isNull(updaters) || updaters.isEmpty())
            return;

        updaters.forEach(updater -> {
            if (Destroyable.class.isAssignableFrom(updater.getClass()))
                ((Destroyable) updater).destroy();
        });
        updaters.clear();
    }

    public void addUpdater(Updatable updater) {
        if (updaters == null)
            updaters = new CopyOnWriteArrayList<>();

        updaters.add(updater);
    }

    public boolean removeUpdater(Updatable updater) {
        if (updaters == null) {
            updaters = new CopyOnWriteArrayList<>();
            return false;
        }

        return updaters.remove(updater);
    }
}
