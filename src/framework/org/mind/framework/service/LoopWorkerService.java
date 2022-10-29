package org.mind.framework.service;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 利用独立线程执行循环处理工作服务类
 *
 * @author dp
 */
public abstract class LoopWorkerService extends AbstractService {

    static final Logger logger = LoggerFactory.getLogger(LoopWorkerService.class);

    private volatile boolean isLoop = true;

    private Thread workerMainThread;

    @Getter
    @Setter
    private long spaceTime;

    @Getter
    @Setter
    private boolean daemon;

    public LoopWorkerService() {
        super();
    }

    public LoopWorkerService(long spaceTime) {
        super();
        this.spaceTime = spaceTime;
    }

    @Override
    public final void start() {
        serviceState = STARTED;
        prepareStart();

        if (workerMainThread == null) {
            workerMainThread = new Thread(new Worker(), this.serviceName);
            workerMainThread.setDaemon(this.daemon);
        }

        if (spaceTime <= 0)
            logger.warn("The space time is {}(ms)", spaceTime);

        if (!workerMainThread.isAlive())
            workerMainThread.start();
    }

    @Override
    public final void stop() {
        serviceState = STOPPED;
        prepareStop();
        isLoop = false;

        if (workerMainThread != null)
            workerMainThread.interrupt();
    }

    protected void prepareStart() {
    }

    protected void prepareStop() {
    }

    /**
     * 服务线程刚开始时调用的方法，若需做一些初始化操作可覆盖此方法来添加
     */
    protected void toStart() {
        logger.info("service [{}] is to start ....", serviceName);
    }

    /**
     * 服务线程将要结束时调用的方法，若需做一些清理操作可覆盖此方法来添加
     */
    protected void toEnd() {
        logger.info("service [{}] is to end ....", serviceName);
    }

    @Override
    public boolean isStart() {
        return workerMainThread != null && workerMainThread.isAlive();
    }

    /*
     * 循环处理内容
     */
    protected abstract void doLoopWork();

    private class Worker implements Runnable {
        @Override
        public void run() {
            toStart();
            while (isLoop) {
                // process child thread
                doLoopWork();

                // sleep
                if (spaceTime > 0) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(spaceTime);
                    } catch (InterruptedException e) {}
                    Thread.yield();
                } else // 通过spaceTime<=0跳出for循环
                    break;
            }
            toEnd();
        }
    }

    public void setDaemon(boolean on) {
        this.daemon = on;
        if (workerMainThread != null)
            workerMainThread.setDaemon(on);
    }

    protected void interrupt() {
        if (workerMainThread != null && workerMainThread.isAlive())
            workerMainThread.interrupt();
    }
}
