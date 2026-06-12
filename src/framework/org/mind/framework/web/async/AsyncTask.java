package org.mind.framework.web.async;

/**
 * 携带 AsyncContext 的任务包装器，供拒绝策略访问响应对象
 *
 * @author: Marcus
 * @date: 2026/6/8
 * @version: 1.0
 */
public class AsyncTask implements Runnable {
    private final Runnable delegate;
    private final AsyncRequestContext asyncContext;

    public AsyncTask(AsyncRequestContext asyncContext, Runnable delegate) {
        this.delegate = delegate;
        this.asyncContext = asyncContext;
    }

    @Override
    public void run() {
        delegate.run();
    }

    public AsyncRequestContext getAsyncContext() {
        return asyncContext;
    }
}
