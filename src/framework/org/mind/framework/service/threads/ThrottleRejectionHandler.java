package org.mind.framework.service.threads;

import org.mind.framework.util.ViewResolver;
import org.mind.framework.web.async.AsyncContextHelper;
import org.mind.framework.web.async.AsyncTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.LongAdder;

/**
 *
 * @author: Marcus
 * @date: 2026/6/8
 * @version: 1.0
 */
public class ThrottleRejectionHandler implements RejectedExecutionHandler {
    private static final Logger log = LoggerFactory.getLogger(ThrottleRejectionHandler.class);
    private final LongAdder rejectedCount = new LongAdder();

    @Override
    public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
        rejectedCount.increment();
        log.warn("Async task rejected, total: {}", rejectedCount.sum());

        if (task instanceof AsyncTask asyncTask) {
            AsyncContextHelper.writeAndComplete(
                    asyncTask,
                    ViewResolver.response(HttpStatus.TOO_MANY_REQUESTS)
            );
        } else {
            log.error("Trigger thread pool 'ThrottleRejectionHandler' policy, executor state - active: {}, pool: {}, size: {}",
                    executor.getActiveCount(), executor.getPoolSize(), executor.getQueue().size());
            throw new RejectedExecutionException("Task rejected: " + task);
        }
    }
}
