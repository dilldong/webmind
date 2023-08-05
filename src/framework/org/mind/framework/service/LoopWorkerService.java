package org.mind.framework.service;

import lombok.Getter;
import lombok.Setter;
import org.mind.framework.service.threads.ExecutorFactory;
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
    private Thread monitorThread;

    /**
     * default: 15ms
     */
    @Getter
    @Setter
    private long spaceTime = 15L;

    @Getter
    @Setter
    private boolean daemon = false;

    public LoopWorkerService() {
        super();
    }

    public LoopWorkerService(String serviceName) {
        super.setServiceName(serviceName);
    }

    @Override
    public void start() {
        serviceState = STARTED;
        prepareStart();

        if (Objects.isNull(workerMainThread))
            workerMainThread = ExecutorFactory.newThread(serviceName, daemon, new Worker());

        if (spaceTime <= 0)
            logger.warn("The space time is {}(ms)", spaceTime);

        this.monitorAndRestart();
    }

    @Override
    public void stop() {
        serviceState = STOPPED;
        isLoop = false;
        prepareStop();

        if (Objects.nonNull(workerMainThread) && workerMainThread.isAlive()) {
            try {
                workerMainThread.interrupt();
            } catch (Exception ignored) {}
        }
    }

    protected void monitorAndRestart() {
        monitorThread = ExecutorFactory.newDaemonThread("Loop-Monitor", () -> {
            while (true) {
                if (!workerMainThread.isAlive())
                    workerMainThread.start();

                try {
                    TimeUnit.SECONDS.sleep(30L);
                } catch (InterruptedException ignored) {}
            }
        });
        monitorThread.start();
    }

    protected void prepareStart() {
    }

    protected void prepareStop() {
        if (Objects.nonNull(monitorThread) && monitorThread.isAlive()) {
            try {
                monitorThread.interrupt();
            } catch (Exception ignored) {}
        }
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
                } else // jump out of the while loop with spaceTime<=0
                    break;
            }
            toEnd();
        }
    }
}
