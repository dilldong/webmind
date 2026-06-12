package org.mind.framework.web.async;

import lombok.Getter;
import org.mind.framework.web.dispatcher.handler.HandlerRequest;
import org.slf4j.MDC;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author: Marcus
 * @date: 2026/6/11
 * @version: 1.0
 */
@Getter
public class AsyncRequestContext {
    private final AsyncContext asyncContext;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final Map<String, String> mdcSnapshot;
    private final HandlerRequest handlerRequest;
    private final AtomicReference<State> state = new AtomicReference<>(State.RUNNING);

    public enum State {
        RUNNING,      // 业务线程进行中
        TIMED_OUT,    // 超时先到
        COMPLETED     // 业务线程先完成
    }

    public AsyncRequestContext(HandlerRequest handler,
                               AsyncContext asyncContext,
                               HttpServletRequest request,
                               HttpServletResponse response) {
        this.handlerRequest = handler;
        this.asyncContext = asyncContext;
        this.request = request;
        this.response = response;
        this.mdcSnapshot = MDC.getCopyOfContextMap();
    }

    /**
     * 业务线程完成时调用，CAS 抢占完成
     * 返回 true 表示抢到了，写响应
     */
    public boolean tryComplete() {
        return state.compareAndSet(State.RUNNING, State.COMPLETED);
    }

    /**
     * 超时调用，CAS 抢占完成
     * 返回 true 表示抢到了，业务线程还没完成
     * 与 Exception & Error 合并使用同一个
     */
    public boolean tryTimeout() {
        return state.compareAndSet(State.RUNNING, State.TIMED_OUT);
    }

    public boolean isCancelled(){
        return state.get() == State.TIMED_OUT;
    }

    public boolean isCompleted(){
        return state.get() == State.COMPLETED;
    }
}