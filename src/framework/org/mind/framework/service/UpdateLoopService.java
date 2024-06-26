package org.mind.framework.service;

import lombok.Getter;
import lombok.Setter;
import org.mind.framework.web.Destroyable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 循环更新服务
 *
 * @since 2011.06
 * @author dp
 */
public class UpdateLoopService extends LoopWorkerService {

    @Getter
    @Setter
    private List<Updatable> updaters;

    public UpdateLoopService() {
        super();
    }

    public UpdateLoopService(String serviceName) {
        super(serviceName);
    }

    @Override
    protected void doLoopWork() {
        if (Objects.isNull(updaters) || updaters.isEmpty())
            return;

        updaters.forEach(updatable -> {
            if (Objects.nonNull(updatable))
                updatable.doUpdate();
        });
    }

    @Override
    protected void prepareStop() {
        super.prepareStop();
        if (Objects.isNull(updaters) || updaters.isEmpty())
            return;

        updaters.forEach(updater -> {
            if (updater instanceof Destroyable)
                ((Destroyable) updater).destroy();
        });
        updaters.clear();
    }

    public void addUpdater(Updatable updater) {
        if (Objects.isNull(updaters))
            updaters = new CopyOnWriteArrayList<>();

        updaters.add(updater);
    }

    public boolean removeUpdater(Updatable updater) {
        if (Objects.isNull(updaters)) {
            updaters = new CopyOnWriteArrayList<>();
            return false;
        }

        return updaters.remove(updater);
    }
}
