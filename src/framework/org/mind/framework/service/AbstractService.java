package org.mind.framework.service;

/**
 * 服务模板类
 *
 * @author dp
 */
public abstract class AbstractService implements Service {

    protected String serviceName = getClass().getSimpleName();

    protected int serviceState;

    public static final int NEW = 0;

    public static final int STARTED = 1;

    public static final int STOPPED = 2;

    public AbstractService() {
    }

    public AbstractService(String serviceName) {
        super();
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public boolean isStart() {
        return serviceState == STARTED;
    }

    public boolean isStop() {
        return serviceState == STOPPED;
    }

    public int getServiceState() {
        return serviceState;
    }
}
