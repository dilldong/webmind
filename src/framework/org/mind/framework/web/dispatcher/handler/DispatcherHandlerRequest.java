package org.mind.framework.web.dispatcher.handler;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.mind.framework.ContextSupport;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.http.Response;
import org.mind.framework.util.DateUtils;
import org.mind.framework.util.HttpUtils;
import org.mind.framework.util.JsonUtils;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.util.RandomCodeUtil;
import org.mind.framework.util.ViewResolver;
import org.mind.framework.web.Action;
import org.mind.framework.web.async.AsyncRequestContext;
import org.mind.framework.web.container.ContainerAware;
import org.mind.framework.web.dispatcher.support.Catcher;
import org.mind.framework.web.dispatcher.support.ConverterFactory;
import org.mind.framework.web.interceptor.DefaultUploadErrorInterceptor;
import org.mind.framework.web.interceptor.ErrorInterceptor;
import org.mind.framework.web.interceptor.HandlerInterceptor;
import org.mind.framework.web.renderer.Render;
import org.mind.framework.web.renderer.TextRender;
import org.mind.framework.web.server.WebServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;


/**
 * Dispatcher handles ALL requests from clients, and dispatches to appropriate
 * handler to handle each request.
 *
 * @author dp
 */
public class DispatcherHandlerRequest implements HandlerRequest, HandlerResult {
    private static final Logger log = LoggerFactory.getLogger("RequestHandler");
    private static final Map<String, ExtendedLogger> TARGET_LOG_CACHEMAP = new ConcurrentHashMap<>();

    private Map<String, Execution> actions;// URI regex mapping object
    private List<String> uriRegexList; // URI regex list

    // interceptor mapping
    private List<Catcher> interceptorsCatcher;

    // resource handler.
    private ResourceRequest resourceRequest;

    // MultipartResolver used by this servlet
    private MultipartResolver multipartResolver;

    // upload size exceeded exception
    private ErrorInterceptor multipartException;

    // gloabl exception
    @Getter
    private ErrorInterceptor gloablException;

    @Override
    public void init(ContainerAware container) throws ServletException {
        this.uriRegexList = new ArrayList<>();
        this.interceptorsCatcher = new ArrayList<>();

        // init Action Maps
        this.actions = new HashMap<>(16) {
            @Override
            public Execution put(String key, Execution value) {
                // convert URI to Regex
                int argNumbers = MatcherUtils.checkCount(key, MatcherUtils.URI_PARAM_PATTERN);
                String regexKey = argNumbers > 0 ? MatcherUtils.convertURI(key) : key;
                if (this.containsKey(regexKey)) {
                    log.error("URI mapping is a globally unique, and can not be repeated: [{}]", key);
                    throw new IllegalArgumentException(String.format("URI mapping is a globally unique, and can not be repeated: [%s]", key));
                }

                // find args number
                value.setArgsNumber(argNumbers);

                // add uri regex to List
                if (!key.equals(regexKey))
                    uriRegexList.add(regexKey);

                value.addRegex(regexKey);
                return super.put(regexKey, value);
            }
        };

        /*
         * init Action by Spring Container and create on the URI mapping relationship
         */
        List<Object> beanList = container.loadBeans(true);
        List<Catcher> mappingInterceptor = new ArrayList<>();
        for (Object bean : beanList) {
            container.loadInterceptor(bean, mappingInterceptor::add);
            container.loadMapping(bean, this.actions::put);
            container.loadCorsOrigin(bean, this.interceptorsCatcher::add);
        }

        // Interceptor forward sorting
        if (mappingInterceptor.size() > 1)
            Collections.sort(mappingInterceptor);

        // add to catcher list
        this.interceptorsCatcher.addAll(mappingInterceptor);

        // init MultipartResolver
        this.initMultipartResolver();
        this.initMultipartException();

        // init global exception
        this.initGloablException();

        // init ResourceHandler
        this.initResourceHandler(container.getServletConfig());
    }

    protected void initMultipartResolver() {
        try {
            this.multipartResolver = ContextSupport.getBean(MULTIPART_RESOLVER_BEAN_NAME, MultipartResolver.class);
        } catch (NoSuchBeanDefinitionException e) {
            // Default is no multipart resolver.
            this.multipartResolver = null;
        }
    }

    protected void initMultipartException() {
        try {
            this.multipartException = ContextSupport.getBean(MULTIPART_EXCEPTION, ErrorInterceptor.class);
        } catch (NoSuchBeanDefinitionException e) {
            // Default is ResourceHandlerRequest resolver.
            this.multipartException = new DefaultUploadErrorInterceptor();
        }
    }

    protected void initGloablException() {
        try {
            this.gloablException = ContextSupport.getBean(GLOBAL_EXCEPTION, ErrorInterceptor.class);
        } catch (NoSuchBeanDefinitionException ignored) {
        }
    }

    protected void initResourceHandler(ServletConfig servletConfig) {
        try {
            this.resourceRequest = ContextSupport.getBean(RESOURCE_HANDLER_BEAN_NAME, ResourceRequest.class);
        } catch (NoSuchBeanDefinitionException e) {
            // Default is ResourceHandlerRequest resolver.
            this.resourceRequest = new ResourceHandlerRequest(servletConfig);
        }
    }

    @Override
    public synchronized void destroy() {
        if (!actions.isEmpty())
            actions.clear();

        if (!uriRegexList.isEmpty())
            uriRegexList.clear();

        if (!interceptorsCatcher.isEmpty())
            interceptorsCatcher.clear();

        if (!TARGET_LOG_CACHEMAP.isEmpty())
            TARGET_LOG_CACHEMAP.clear();
    }

    @Override
    public void processor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        final long begin = DateUtils.CachedTime.currentMillis();

        // customize response
        this.customizeResponse(request, response);

        // check request is multipart request
        HttpServletRequest processedRequest = checkMultipart(request, response);
        if (Objects.isNull(processedRequest))
            return;

        final String requestUri = HttpUtils.getURI(processedRequest, false);

        // find action
        Execution execution = resolveExecution(requestUri, processedRequest, response);
        if (Objects.isNull(execution))
            return;

        // pre-interceptor
        final List<HandlerInterceptor> currentInterceptors = new ArrayList<>();
        if (doBeforeInterceptors(requestUri, processedRequest, response, currentInterceptors))
            return;

        // logging
        if (execution.isRequestLog() && !execution.isSimpleLogging())
            targetLog(execution, new ParameterizedMessage("[{}]", requestUri));

        // execute action
        Action.setActionContext(processedRequest, response);
        try {
            execute(requestUri, execution, processedRequest, response, currentInterceptors);
        } catch (Throwable ex) {
            Throwable root = ThrowProvider.unwrapCause(ex);
            boolean result =
                    Objects.nonNull(getGloablException())
                            && getGloablException().handleFailure(processedRequest, response, root);

            if (!result)
                ThrowProvider.doThrow(root);
        } finally {
            Action.removeActionContext();

            if (execution.isRequestLog()) {
                long spendor = DateUtils.CachedTime.currentMillis() - begin;
                this.targetLog(execution,
                        execution.isSimpleLogging() ?
                                new ParameterizedMessage("[{}] - [{}ms]", requestUri, spendor) :
                                new ParameterizedMessage("Used time(ms): {}", spendor)
                );
            }
        }
    }

    @Override
    public void execute(String requestUri,
                        Execution execution,
                        HttpServletRequest request,
                        HttpServletResponse response,
                        List<HandlerInterceptor> interceptors) throws ServletException, IOException {
        // 1. resolve arguments
        Object[] args = resolveArguments(execution, requestUri);

        // 2. execute action
        Object result = execution.execute(args);

        // 3. Post-interceptors
        interceptors.forEach(i -> i.doAfter(request, response));

        // 4. resolver result
        handleResult(result, request, response);

        // 5. renderCompletion
        interceptors.forEach(i -> i.renderCompletion(request, response));
    }

    @Override
    public void execute(String requestUri,
                        Execution execution,
                        AsyncRequestContext asyncContext,
                        List<HandlerInterceptor> interceptors) throws ServletException, IOException {
        // 1. resolve arguments
        Object[] args = resolveArguments(execution, requestUri);

        // 2. execute action
        if (asyncContext.isCancelled())
            return;

        Object result = execution.execute(args);

        // 3. Post-interceptors
        if (asyncContext.isCancelled())
            return;

        interceptors.forEach(i -> i.doAfter(asyncContext.getRequest(), asyncContext.getResponse()));

        // 4. resolver result
        if (asyncContext.isCancelled())
            return;

        handleResult(result, asyncContext.getRequest(), asyncContext.getResponse());

        // 5. renderCompletion
        interceptors.forEach(i -> i.renderCompletion(asyncContext.getRequest(), asyncContext.getResponse()));
    }

    // 路由解析，找不到时直接写 404/405 响应
    protected Execution resolveExecution(String requestUri,
                                         HttpServletRequest request,
                                         HttpServletResponse response) throws IOException, ServletException {

        // set default character encoding to "utf-8" if encoding is not set:
        if (StringUtils.isEmpty(request.getCharacterEncoding()))
            request.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // static resource
        if (this.resourceRequest.checkStaticResource(request, response))
            return null;

        // set response no-cache
        this.processNoCache(response);

        /*
         * Find action
         * 1.exact match
         */
        Execution execution = null;
        if (this.actions.containsKey(requestUri)) {
            execution = this.actions.get(requestUri);
        } else {
            // 2. regex match
            for (String regex : this.uriRegexList) {
                Matcher matcher = MatcherUtils.matcher(requestUri, regex, MatcherUtils.DEFAULT_EQ);
                if (!matcher.matches())// not-match
                    continue;

                execution = this.actions.get(regex);
                break;
            }
        }

        if (Objects.isNull(execution)) {
            log.warn("(404) Not found: [{}]", requestUri);
            this.renderError(
                    HttpServletResponse.SC_NOT_FOUND,
                    "The requested URL (404) Not found",
                    Render.NOT_FOUND_HTML, request, response);
            return null;
        }

        // validation request method
        if (!execution.isSupportMethod(request.getMethod())) {
            log.warn("[{}] - HTTP method {} not supported, specified as: {}",
                    requestUri, request.getMethod(), execution.methodString());

            this.renderError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    String.format("This URL does not support '%s'", request.getMethod()),
                    Render.METHOD_NOT_ALLOWED_HTML, request, response);
            return null;
        }
        return execution;
    }

    /**
     * Run the pre-interceptor
     *
     * @return true: has been intercepted else false
     */
    @Override
    public boolean doBeforeInterceptors(String requestUri,
                                        HttpServletRequest request,
                                        HttpServletResponse response,
                                        List<HandlerInterceptor> out) {
        if (Objects.isNull(interceptorsCatcher))
            return false;

        for (Catcher catcher : interceptorsCatcher) {
            if (!catcher.matchOne(requestUri, MatcherUtils.DEFAULT_EQ)) // not-match
                continue;

            HandlerInterceptor interceptor = catcher.getHandler();

            // Interceptor doBefore
            // return false, Return to the request page
            if (!interceptor.doBefore(request, response)) {
                log.debug("Intercept: {} by {}", requestUri, interceptor.getClass().getSimpleName());
                out.clear();
                return true;
            }

            out.add(interceptor);
        }

        return false;
    }

    /**
     * Convert the request into a multipart request, and make multipart resolver available.
     * <p>If no multipart resolver is set, simply use the existing request.
     *
     * @param request current HTTP request
     * @return the processed request (multipart wrapper if necessary)
     * @see MultipartResolver#resolveMultipart
     */
    protected HttpServletRequest checkMultipart(HttpServletRequest request, HttpServletResponse response) {
        if (Objects.isNull(this.multipartResolver))
            return request;

        // request.getDispatcherType() == DispatcherType.REQUEST && this.multipartResolver.isMultipart(request)
        if (HttpUtils.isMultipartRequest(request)) {
            try {
                MultipartHttpServletRequest multipartRequest = this.multipartResolver.resolveMultipart(request);
                request.setAttribute(CHECK_MULTIPART, multipartRequest);
                return multipartRequest;
            } catch (MultipartException e) {
                if (!this.multipartException.handleFailure(request, response, e))
                    return null;
            }
        }

        // return original request
        return request;
    }

    @Override
    public void clear(HttpServletRequest request) {
        HttpUtils.clearRequestAttribute(request);
        if (Objects.isNull(this.multipartResolver))
            return;

        // Clean up any resources used by the given multipart request (if any).
        if (HttpUtils.isMultipartRequest(request)) {
            Object multipartRequest = request.getAttribute(CHECK_MULTIPART);
            if (Objects.isNull(multipartRequest))
                return;

            this.multipartResolver.cleanupMultipart((MultipartHttpServletRequest) multipartRequest);
            request.removeAttribute(CHECK_MULTIPART);

            log.debug("Cleanup Multipart: [{}]", HttpUtils.getURI(request));
        }
    }

    @Override
    public void handleResult(Object result, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (Objects.isNull(result))
            return;

        ConverterFactory converterFactory = ConverterFactory.getInstance();
        if (converterFactory.isConvert(result.getClass())) {
            Render.stringRender(result.toString()).render(request, response);
            return;
        }

        if (result instanceof Render render) {
            render.render(request, response);
            return;
        }

        new TextRender(JsonUtils.toJson(result)).render(request, response);
    }

    /**
     * Set the no-cache headers for all responses, if requested.
     * <strong>NOTE</strong> - This header will be overridden
     * automatically if a <code>RequestDispatcher.forward()</code> call is
     * ultimately invoked.
     *
     * @param response The servlet response we are creating
     */
    protected void processNoCache(HttpServletResponse response) {
        // HTTP/1.0
        response.setHeader(HttpHeaders.PRAGMA, HandlerResult.NO_CACHE);
        // HTTP/1.1+
        response.setHeader(HttpHeaders.CACHE_CONTROL, HandlerResult.NO_CACHE);
        response.setDateHeader(HttpHeaders.EXPIRES, 0L);
    }

    protected Object[] resolveArguments(Execution execution, String requestUri) {
        int number = execution.getArgsNumber();
        if (number == 0)
            return null;

        Object[] args = new Object[number];
        Matcher matcher = null;

        for (String regex : execution.getRegex()) {
            matcher = MatcherUtils.matcher(requestUri, regex, MatcherUtils.DEFAULT_EQ);
            if (matcher.matches())
                break;
        }

        // Fetch request parameters in the URI
        Class<?> type;
        for (int i = 0; i < number; ++i) {
            try {
                type = execution.getParameterTypes()[i];
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new IllegalArgumentException("[" + requestUri + "] - Method is missing URL parameter. " + e.getMessage());
            }

            if (String.class.getName().equals(type.getName()))
                args[i] = matcher.group(i + 1);// segmentation fetch
            else {
                try {
                    args[i] = ConverterFactory.getInstance().convert(type, matcher.group(i + 1));
                } catch (NumberFormatException | NullPointerException e) {
                    throw new IllegalArgumentException("[" + requestUri + "] - URL parameters type was incorrect. " + e.getMessage());
                }
            }
        }
        return args;
    }

    @Override
    public void renderError(int statusCode,
                            String jsonMessage,
                            String htmlMessage,
                            HttpServletRequest request,
                            HttpServletResponse response) throws ServletException, IOException {
        response.setStatus(statusCode);
        String contentType = request.getContentType();
        // json body
        if (StringUtils.isNotEmpty(contentType) && contentType.contains(MediaType.APPLICATION_JSON_VALUE))
            ViewResolver.text(new Response<String>(statusCode, jsonMessage).toJson())
                    .render(request, response);
        else// html body
            ViewResolver.text(htmlMessage).render(request, response);
    }

    @Override
    public void customizeResponse(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader("X-Powered-By", WebServerConfig.POWER_BY_NAME);
        response.addHeader(
                HandlerResult.REQUEST_ID,
                StringUtils.defaultIfEmpty(
                        MDC.get(HandlerResult.REQUEST_IN_LOG),
                        RandomCodeUtil.fastRandomString(6))
        );
    }

    /**
     * Use {@link StackTraceElement} to mask the log source as an Action class,
     * so logs are directly pinpointed to the business method
     */
    protected void targetLog(Execution execution, ParameterizedMessage message) {
        Class<?> target = execution.getActionInstance().getClass();
        ExtendedLogger targetLoger = TARGET_LOG_CACHEMAP.computeIfAbsent(
                target.getName(),
                className -> (ExtendedLogger) LogManager.getLogger(className)
        );

        StackTraceElement location = new StackTraceElement(
                target.getName(),                   // the fully declaring class name of class
                execution.getMethod().getName(),    // method name
                target.getSimpleName() + ".java",   // the file name of class
                0                                   // line number(No got it)
        );

        targetLoger.logMessage(
                Level.INFO,
                null,
                target.getName(),
                location,
                message,
                null
        );
    }

}
