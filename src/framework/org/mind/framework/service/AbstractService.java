package org.mind.framework.service;

/**
 * 服务模板类
 *
 * @author dongping
 */
public abstract class AbstractService implements Service {

    protected String serviceName;

    protected int serviceState;

    public static final int STATE_NEW = 0;

    public static final int STATE_STARTED = 1;

    public static final int STATE_STOPED = 2;

    public AbstractService() {
        this.serviceName = String.format("%s@%s",
                getClass().getSimpleName(), Integer.toHexString(getClass().hashCode()));
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
        return serviceState == STATE_STARTED;
    }

    public boolean isStop() {
        return serviceState == STATE_STOPED;
    }

    public int getServiceState() {
        return serviceState;
    }
}
