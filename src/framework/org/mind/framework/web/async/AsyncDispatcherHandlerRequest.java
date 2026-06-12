package org.mind.framework.web.async;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.util.DateUtils;
import org.mind.framework.util.HttpUtils;
import org.mind.framework.util.ViewResolver;
import org.mind.framework.web.Action;
import org.mind.framework.web.dispatcher.handler.DispatcherHandlerRequest;
import org.mind.framework.web.dispatcher.handler.Execution;
import org.mind.framework.web.interceptor.HandlerInterceptor;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author: Marcus
 * @date: 2026/6/9
 * @version: 1.0
 */
public class AsyncDispatcherHandlerRequest extends DispatcherHandlerRequest {

    private final AsyncServletExecutorService asyncExecutor;

    public AsyncDispatcherHandlerRequest(AsyncServletExecutorService asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    public void processor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        final long begin = DateUtils.CachedTime.currentMillis();

        // customize response
        this.customizeResponse(request, response);

        // check request is multipart request
        HttpServletRequest processedRequest = this.checkMultipart(request, response);
        if (Objects.isNull(processedRequest))
            return;

        final String requestUri = HttpUtils.getURI(processedRequest, false);

        // find action
        final Execution execution = resolveExecution(requestUri, processedRequest, response);

        if (Objects.isNull(execution))
            return;

        if (!execution.getAsync().value()) {
            super.processor(request, response);
            return;
        }

        // Pre-interceptor
        List<HandlerInterceptor> currentInterceptors = new ArrayList<>();
        if (doBeforeInterceptors(requestUri, processedRequest, response, currentInterceptors))
            return;

        // Start async
        final AsyncRequestContext asyncContext =
                AsyncContextHelper.startAsync(this, processedRequest, response, execution.getAsync().timeoutMs());

        asyncExecutor.submit(asyncContext, () -> {
            // 1. recovery MDC
            if (Objects.nonNull(asyncContext.getMdcSnapshot()))
                MDC.setContextMap(asyncContext.getMdcSnapshot());

            // 2. recovery Action ThreadLocal
            Action.setActionContext(asyncContext.getRequest(), asyncContext.getResponse());
            try {
                if (execution.isRequestLog() && !execution.isSimpleLogging())
                    targetLog(execution, new ParameterizedMessage("[{}]", requestUri));

                // execute
                execute(requestUri, execution, asyncContext, currentInterceptors);

                // 这里未处理返回值, 当抢到了完成权, 走 finally -> completeQuietly(), 否则会跳过 complete()
                asyncContext.tryComplete();

            } catch (Throwable ex) {
                if (asyncContext.tryTimeout()) {
                    Throwable root = ThrowProvider.unwrapCause(ex);
                    boolean result =
                            Objects.nonNull(getGloablException()) &&
                                    getGloablException().handleFailure(asyncContext.getRequest(), asyncContext.getResponse(), root);

                    if (!result)
                        AsyncContextHelper.writeAndComplete(asyncContext, ViewResolver.response(500, root.getMessage()));
                }
            } finally {
                Action.removeActionContext();

                if (asyncContext.isCompleted()) {
                    clear(asyncContext.getRequest());
                    AsyncContextHelper.completeQuietly(asyncContext.getAsyncContext());
                }

                if (execution.isRequestLog()) {
                    long spendor = DateUtils.CachedTime.currentMillis() - begin;
                    targetLog(execution,
                            execution.isSimpleLogging() ?
                                    new ParameterizedMessage("[{}] - [{}ms]", requestUri, spendor) :
                                    new ParameterizedMessage("Used time(ms): {}", spendor)
                    );
                }

                MDC.clear();
            }
            return null;
        });
    }
}
