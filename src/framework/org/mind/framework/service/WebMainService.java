package org.mind.framework.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 提供WEB应用服务，允许使用多个相同Service对象构建服务
 *
 * @author dp
 */
public class WebMainService extends AbstractService {

    static final Logger logger = LoggerFactory.getLogger(WebMainService.class);

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
                            logger.info("Service [{}@{}] to start ....",
                                    serv.getClass().getName(),
                                    Integer.toHexString(serv.hashCode()));

                            serv.start();
                        }
                    });
                    t.start();
                }
            }
        }
    }

    protected void stopChildServices() {
        if (childServices != null) {
            for (final Service serv : childServices) {
                if (serv != null) {
                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            logger.info("Service [{}@{}] to stop ....",
                                    serv.getClass().getName(),
                                    Integer.toHexString(serv.hashCode()));

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
