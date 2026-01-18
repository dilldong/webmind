package org.mind.framework.web.dispatcher.handler;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.mind.framework.util.ViewResolver;
import org.mind.framework.web.Action;
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
    private List<String> urisRegex; // URI regex list

    // interceptor mapping
    private List<Catcher> interceptorsCatcher;

    // resource handler.
    private ResourceRequest resourceRequest;

    // MultipartResolver used by this servlet
    private MultipartResolver multipartResolver;

    // upload size exceeded exception
    private ErrorInterceptor multipartException;

    @Override
    public void init(ContainerAware container) throws ServletException {
        this.urisRegex = new ArrayList<>();
        this.interceptorsCatcher = new ArrayList<>();

        // init Action Maps, support hot load, so used java.util.concurrent.ConcurrentHashMap.
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
                    urisRegex.add(regexKey);

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

        if (!urisRegex.isEmpty())
            urisRegex.clear();

        if (!interceptorsCatcher.isEmpty())
            interceptorsCatcher.clear();

        if (!TARGET_LOG_CACHEMAP.isEmpty())
            TARGET_LOG_CACHEMAP.clear();

        actions = null;
        urisRegex = null;
        interceptorsCatcher = null;
    }

    @Override
    public void processor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        final long begin = DateUtils.CachedTime.currentMillis();

        // customize response
        this.customizeResponse(request, response);

        // check request is multipart request.
        HttpServletRequest processedRequest = this.checkMultipart(request, response);
        if (Objects.isNull(processedRequest))
            return;

        final String requestURI = HttpUtils.getURI(processedRequest, false);

        /*
         * Global interceptors for application containers
         *
         * interceptor.doBefore(execution, request, response);
         * doAfter();
         * renderCompletion();
         */
        List<HandlerInterceptor> currentInterceptors = new ArrayList<>();
        if (Objects.nonNull(interceptorsCatcher)) {
            for (Catcher catcher : interceptorsCatcher) {
                if (!catcher.matchOne(requestURI, MatcherUtils.DEFAULT_EQ))// not-match
                    continue;

                HandlerInterceptor interceptor = catcher.getHandler();

                // Interceptor doBefore
                // return false, Return to the request page
                if (!interceptor.doBefore(processedRequest, response)) {
                    log.debug("Intercept access request URI: {}, The interception class is: {}", requestURI, interceptor.getClass().getSimpleName());
                    return;
                }

                // Continue to use later: doAfter(), renderCompletion()
                currentInterceptors.add(interceptor);
            }
        }

        // set default character encoding to "utf-8" if encoding is not set:
        if (StringUtils.isEmpty(processedRequest.getCharacterEncoding()))
            processedRequest.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // static resource
        if (this.resourceRequest.checkStaticResource(processedRequest, response))
            return;

        // set response no-cache
        this.processNoCache(response);

        /*
         * find and process action
         */
        Execution execution = null;
        Object[] args = null;

        // exact match
        if (this.actions.containsKey(requestURI)) {
            execution = this.actions.get(requestURI);
            args = this.checkRequestArguments(execution);
        } else {// regex find match
            Matcher matcher;
            for (String regex : this.urisRegex) {
                matcher = MatcherUtils.matcher(requestURI, regex, MatcherUtils.DEFAULT_EQ);
                if (!matcher.matches())// not-match
                    continue;

                execution = this.actions.get(regex);
                args = this.checkRequestArguments(execution, matcher, requestURI);
                break;
            }
        }

        /*
         * Status code (404) indicating that the requested resource is not available.
         */
        if (Objects.isNull(execution)) {
            log.warn("The requested URL (404) Not found: [{}]", requestURI);
            this.renderError(
                    HttpServletResponse.SC_NOT_FOUND,
                    "The requested URL (404) Not found",
                    Render.NOT_FOUND_HTML,
                    processedRequest,
                    response);
            return;
        }

        /*
         * validation request method
         */
        if (!execution.isSupportMethod(processedRequest.getMethod())) {
            log.warn("[{}] - HTTP method {} is not supported by this URI, specified as: {}",
                    requestURI, processedRequest.getMethod(), execution.methodString());
            this.renderError(
                    HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    String.format("This URL does not support the HTTP method '%s'", processedRequest.getMethod()),
                    Render.METHOD_NOT_ALLOWED_HTML,
                    processedRequest,
                    response);
            return;
        }

        if (execution.isRequestLog() && !execution.isSimpleLogging()) {
            this.targetLog(execution, new ParameterizedMessage("[{}]", requestURI));
        }

        // execute action
        Object result;
        try {
            Action.setActionContext(processedRequest, response);
            result = execution.execute(args);

            // Interceptor doAfter
            if (!currentInterceptors.isEmpty()) {
                for (HandlerInterceptor interceptor : currentInterceptors)
                    interceptor.doAfter(processedRequest, response);
            }

            // resolver result
            this.handleResult(result, processedRequest, response);

            // Interceptor renderCompletion
            if (!currentInterceptors.isEmpty())
                currentInterceptors.forEach(interceptor -> interceptor.renderCompletion(processedRequest, response));

        } catch (IOException | ServletException e) {
            throw e;
        } catch (Throwable e) {
            Throwable c = Objects.isNull(e.getCause()) ? e : e.getCause();
            if (c instanceof IOException || c instanceof ServletException)
                ThrowProvider.doThrow(c);
            else
                throw new ServletException(c.getMessage(), c);// other exception throws with ServletException.
        } finally {
            Action.removeActionContext();
            HandlerRequest.super.clear(request);

            if (execution.isRequestLog()) {
                long spendor = DateUtils.CachedTime.currentMillis() - begin;
                this.targetLog(execution,
                        execution.isSimpleLogging() ?
                                new ParameterizedMessage("[{}] - [{}ms]", requestURI, spendor) :
                                new ParameterizedMessage("Used time(ms): {}", spendor)
                );
            }
        }
    }

    private void targetLog(Execution execution, ParameterizedMessage message) {
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
        if (Objects.isNull(this.multipartResolver))
            return;

        // Clean up any resources used by the given multipart request (if any).
        if (HttpUtils.isMultipartRequest(request)) {
            Object multipartRequest = request.getAttribute(CHECK_MULTIPART);
            if (Objects.isNull(multipartRequest))
                return;

            this.multipartResolver.cleanupMultipart((MultipartHttpServletRequest) multipartRequest);
            request.removeAttribute(CHECK_MULTIPART);

            log.debug("Cleanup Multipart....");
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

    protected Object[] checkRequestArguments(Execution execution, Matcher matcher, String requestURI) {
        Object[] args = null;
        int number = execution.getArgsNumber();
        if (number > 0) {
            args = new Object[number];

            // Fetch request parameters in the URI
            Class<?> type;
            for (int i = 0; i < number; ++i) {
                try {
                    type = execution.getParameterTypes()[i];
                } catch (ArrayIndexOutOfBoundsException e) {
                    throw new IllegalArgumentException("[" + requestURI + "] - Method is missing URL parameter. " + e.getMessage());
                }

                if (String.class.getName().equals(type.getName()))
                    args[i] = matcher.group(i + 1);// segmentation fetch
                else {
                    try {
                        args[i] = ConverterFactory.getInstance().convert(type, matcher.group(i + 1));
                    } catch (NumberFormatException | NullPointerException e) {
                        throw new IllegalArgumentException("[" + requestURI + "] - URL parameters type was incorrect. " + e.getMessage());
                    }
                }
            }
        }
        return args;
    }

    protected Object[] checkRequestArguments(Execution execution) {
        int number = execution.getArgsNumber();
        if (number > 0)
            return new Object[number];
        return null;
    }

    protected void renderError(int statusCode, String jsonMessage, String htmlMessage, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setStatus(statusCode);
        String contentType = request.getContentType();
        // json body
        if (StringUtils.isNotEmpty(contentType) && contentType.contains(MediaType.APPLICATION_JSON_VALUE))
            ViewResolver.text(new Response<String>(statusCode, jsonMessage).toJson())
                    .render(request, response);
        else// html body
            ViewResolver.text(htmlMessage).render(request, response);
    }

    protected void customizeResponse(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader("X-Powered-By", WebServerConfig.POWER_BY_NAME);
    }
}
