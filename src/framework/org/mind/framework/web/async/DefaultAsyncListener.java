package org.mind.framework.web.async;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import org.mind.framework.service.threads.ThreadContextPropagator;
import org.mind.framework.util.ViewResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 超时 & 异常兜底
 *
 * @author: Marcus
 * @date: 2026/6/8
 * @version: 1.0
 */
public class DefaultAsyncListener implements AsyncListener {
    private static final Logger log = LoggerFactory.getLogger(DefaultAsyncListener.class);

    private final AsyncRequestContext context;

    public DefaultAsyncListener(AsyncRequestContext context) {
        this.context = context;
    }

    @Override
    public void onComplete(AsyncEvent event) throws IOException {
        // do nothing
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
        // CAS 抢占：如果业务线程已经完成（tryTimeout 返回 false），什么都不做
        if(!context.tryTimeout())
            return;

        ThreadContextPropagator.restore(context.getMdcSnapshot(), () -> {
            log.warn("[504] Async request timed out");
            AsyncContextHelper.writeAndComplete(
                    context, ViewResolver.response(504, "Handle async request timeout")
            );
        });
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {
        // 超时和错误共用同一个 CAS 逻辑
        if (!context.tryTimeout())
            return;

        ThreadContextPropagator.restore(context.getMdcSnapshot(), () -> {
            log.error("[500] Async request error", event.getThrowable());
            AsyncContextHelper.writeAndComplete(
                    context, ViewResolver.response(500, "Internal server error")
            );
        });
    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {
        // do nothing
    }
}
