package org.mind.framework.service.queue;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 自定义拒绝策略，记录拒绝情况
 * @version 1.0
 * @auther Marcus
 * @date 2025/6/2
 */
@Slf4j
public class RejectedExecutionHandler extends ThreadPoolExecutor.CallerRunsPolicy{
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        log.error("Consumer task rejected, executor state - active: {}, pool: {}, size: {}",
                e.getActiveCount(), e.getPoolSize(), e.getQueue().size());
        super.rejectedExecution(r, e);
    }
}
