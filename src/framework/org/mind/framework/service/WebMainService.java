package org.mind.framework.service;

import org.apache.log4j.Logger;

/**
 * 提供WEB应用服务，允许使用多个相同Service对象构建服务
 *
 * @author dp
 */
public class WebMainService extends AbstractService {

    static Logger logger = Logger.getLogger(WebMainService.class);

    private Service[] childServices;

    public WebMainService() {
        this.setServiceName(getClass().getSimpleName());
    }

    @SuppressWarnings("Duplicates")
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

    @SuppressWarnings("Duplicates")
    protected void stopChildServices() {
        if (childServices != null) {
            for (final Service serv : childServices) {
                if (serv != null) {
                    Thread t = new Thread(new Runnable() {
                        @Override
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

    @Override
    public final void start() {
        startChildServices();
        serviceState = STATE_STARTED;
    }

    @Override
    public final void stop() {
        stopChildServices();
        serviceState = STATE_STOPED;
    }

    public Service[] getChildServices() {
        return childServices;
    }

    public void setChildServices(Service[] childServices) {
        this.childServices = childServices;
    }
}
