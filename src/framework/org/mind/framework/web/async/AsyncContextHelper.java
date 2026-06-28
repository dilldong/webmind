package org.mind.framework.web.async;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mind.framework.http.Response;
import org.mind.framework.web.dispatcher.handler.HandlerRequest;
import org.mind.framework.web.renderer.TextRender;
import org.slf4j.LoggerFactory;

/**
 *
 * @author: Marcus
 * @date: 2026/6/8
 * @version: 1.0
 */
public final class AsyncContextHelper {
    private AsyncContextHelper() {
    }

    /**
     * 开启异步并注册 Listener
     */
    public static AsyncRequestContext startAsync(HandlerRequest handler,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response,
                                                 long timeoutMs) {
        AsyncContext ctx = request.startAsync(request, response);
        ctx.setTimeout(timeoutMs);

        AsyncRequestContext context = new AsyncRequestContext(handler, ctx, request, response);
        ctx.addListener(new DefaultAsyncListener(context));
        return context;
    }

    /**
     * 安全地写响应并 complete。
     */
    public static void writeAndComplete(AsyncTask task, Response<?> result) {
        writeAndComplete(task.getAsyncContext(), result);
    }

    /**
     * 安全地写响应并 complete。
     */
    public static void writeAndComplete(AsyncRequestContext context, Response<?> result) {
        if (context.getResponse().isCommitted())
            return;

        try {
            context.getResponse().setStatus(result.getCode());
            new TextRender(result.toJson()).render(context.getRequest(), context.getResponse());
        } catch (Exception e) {
            LoggerFactory.getLogger(AsyncContextHelper.class)
                    .warn("Failed to write async response", e);
        } finally {
            context.getHandlerRequest().clear(context.getRequest());
            completeQuietly(context.getAsyncContext());
        }
    }

    public static void completeQuietly(AsyncContext ctx) {
        try {
            ctx.complete();
        } catch (IllegalStateException ignored) {
            // do nothing
        }
    }
}
