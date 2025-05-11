package org.mind.framework.service;

/**
 * 服务模板类
 *
 * @since 2011.06
 * @author dp
 */
public abstract class AbstractService implements Service {

    protected String serviceName;

    protected volatile int serviceState;

    public static final int NEW = 0;

    public static final int STARTED = 1;

    public static final int STOPPED = 2;

    public AbstractService() {
        serviceName = getClass().getSimpleName();
    }

    public AbstractService(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public boolean isStarted() {
        return serviceState == STARTED;
    }

    @Override
    public boolean isStop() {
        return serviceState == STOPPED;
    }

    @Override
    public int getServiceState() {
        return serviceState;
    }
}
