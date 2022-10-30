package org.mind.framework.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;

/**
 * 提供非WEB应用服务，不允许使用多个相同的对象构建服务
 *
 * @author dp
 */
public class MainService extends AbstractService {

    static final Logger logger = LoggerFactory.getLogger(MainService.class);

    private Set<Service> childServices;

    public MainService() {
        setServiceName(getClass().getSimpleName());
    }

    protected void startChildServices() {
        if (Objects.nonNull(childServices)) {
            childServices.forEach(serv -> {
                if (Objects.nonNull(serv)) {
                    Thread t = new Thread(() -> {
                        if (logger.isInfoEnabled()) {
                            logger.info("Service {}@{} to starting ....",
                                    serv.getClass().getSimpleName(),
                                    Integer.toHexString(serv.hashCode()));
                        }
                        serv.start();
                    });
                    t.start();
                }
            });
        }
    }

    protected void stopChildServices() {
        if (Objects.nonNull(childServices)) {
            childServices.forEach(serv -> {
                if (Objects.nonNull(serv)) {
                    Thread t = new Thread(() -> {
                        if (logger.isInfoEnabled()) {
                            logger.info("Service {}@{} to stop ....",
                                    serv.getClass().getSimpleName(),
                                    Integer.toHexString(serv.hashCode()));
                        }
                        serv.stop();
                    });
                    t.start();
                }
            });
        }
    }

    public final void start() {
        startChildServices();
        serviceState = STARTED;
    }

    public final void stop() {
        stopChildServices();
        serviceState = STOPPED;
        System.gc();
    }

    public Set<Service> getChildServices() {
        return childServices;
    }

    public void setChildServices(Set<Service> childServices) {
        this.childServices = childServices;
    }

}
