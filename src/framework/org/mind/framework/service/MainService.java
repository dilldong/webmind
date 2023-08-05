package org.mind.framework.service;

import org.mind.framework.service.threads.Async;
import org.mind.framework.service.threads.ExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * 异步服务，允许使用多个相同的对象构建
 *
 * @author dp
 * @since 2011.06
 */
public class MainService extends AbstractService {

    static final Logger logger = LoggerFactory.getLogger(MainService.class);

    private List<Service> childServices;

    public MainService() {
        setServiceName(getClass().getSimpleName());
    }

    protected void startChildServices() {
        if(Objects.isNull(childServices) || childServices.isEmpty())
            return;

        childServices.forEach(serv ->
                Async.synchronousExecutor().execute(() -> {
                    if (logger.isInfoEnabled()) {
                        logger.info("Service {}@{} to starting ....",
                                serv.getClass().getSimpleName(),
                                Integer.toHexString(serv.hashCode()));
                    }
                    serv.start();
                })
        );
    }

    protected void stopChildServices() {
        if(Objects.isNull(childServices) || childServices.isEmpty())
            return;

        childServices.forEach(serv ->
                ExecutorFactory.newThread(() -> {
                    if (logger.isInfoEnabled()) {
                        logger.info("Service {}@{} to stopping ....",
                                serv.getClass().getSimpleName(),
                                Integer.toHexString(serv.hashCode()));
                    }
                    serv.stop();
                }).start()
        );
    }

    public final void start() {
        startChildServices();
        serviceState = STARTED;
    }

    public final void stop() {
        stopChildServices();
        serviceState = STOPPED;
    }

    public List<Service> getChildServices() {
        return childServices;
    }

    public void setChildServices(List<Service> childServices) {
        this.childServices = childServices;
    }

}
