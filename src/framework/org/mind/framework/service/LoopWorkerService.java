package org.mind.framework.service;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
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
    private boolean daemon = false;

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

        if (Objects.isNull(workerMainThread))
            workerMainThread = ExecutorFactory.newThread(serviceName, daemon, new Worker());

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

        if (workerMainThread != null && workerMainThread.isAlive())
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
        logger.info("service [{}@{}] is to start ....", serviceName, Integer.toHexString(hashCode()));
    }

    /**
     * 服务线程将要结束时调用的方法，若需做一些清理操作可覆盖此方法来添加
     */
    protected void toEnd() {
        logger.info("service [{}@{}] is to end ....", serviceName, Integer.toHexString(hashCode()));
    }

    @Override
    public boolean isStart() {
        return workerMainThread != null && workerMainThread.isAlive();
    }

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
                    } catch (InterruptedException ignored) {}
                    Thread.yield();
                } else // jump out of the while loop with spaceTime<=0
                    break;
            }
            toEnd();
        }
    }
}
