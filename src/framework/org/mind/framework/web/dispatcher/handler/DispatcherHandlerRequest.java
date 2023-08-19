package org.mind.framework.web.dispatcher.handler;

import org.apache.commons.lang3.StringUtils;
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
import org.mind.framework.web.interceptor.HandlerInterceptor;
import org.mind.framework.web.renderer.Render;
import org.mind.framework.web.renderer.TextRender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;

import javax.servlet.DispatcherType;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;


/**
 * Dispatcher handles ALL requests from clients, and dispatches to appropriate
 * handler to handle each request.
 *
 * @author dp
 */
public class DispatcherHandlerRequest implements HandlerRequest, HandlerResult {

    private static final Logger log = LoggerFactory.getLogger("RequestHandler");

    // MultipartResolver object in the bean factory
    private static final String MULTIPART_RESOLVER_BEAN_NAME = "multipartResolver";

    // ResourceRequest object in the bean factory
    private static final String RESOURCE_HANDLER_BEAN_NAME = "resourceHandlerRequest";

    private Map<String, Execution> actions;// URI regex mapping object
    private List<String> urisRegex; // URI regex list

    // interceptor mapping
    private List<Catcher> interceptorsCatcher;

    // resource handler.
    private ResourceRequest resourceRequest;

    // MultipartResolver used by this servlet
    private MultipartResolver multipartResolver;

    @Override
    public void init(ContainerAware container) throws ServletException {
        this.urisRegex = new ArrayList<>();
        this.interceptorsCatcher = new ArrayList<>();

        // init Action Maps, support hot load, so used java.util.concurrent.ConcurrentHashMap.
        this.actions = new HashMap<String, Execution>() {
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

        // init ResourceHandler
        this.initResourceHanlder(container.getServletConfig());
    }

    protected void initMultipartResolver() {
        try {
            this.multipartResolver = ContextSupport.getBean(MULTIPART_RESOLVER_BEAN_NAME, MultipartResolver.class);
        } catch (NoSuchBeanDefinitionException e) {
            // Default is no multipart resolver.
            this.multipartResolver = null;
        }
    }

    protected void initResourceHanlder(ServletConfig servletConfig) {
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

        actions = null;
        urisRegex = null;
        interceptorsCatcher = null;
    }

    @Override
    public void processor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        final long begin = DateUtils.getMillis();
        final String requestURI = HttpUtils.getURI(request);

        /*
         * Global interceptors for application containers
         *
         * interceptor.doBefore(execution, request, response);
         * doAfter();
         * renderCompletion();
         */
        List<HandlerInterceptor> currentInterceptors = new ArrayList<>();
        if (interceptorsCatcher != null) {
            for (Catcher catcher : interceptorsCatcher) {
                if (!catcher.matchOne(requestURI, MatcherUtils.DEFAULT_EQ))// not-match
                    continue;

                HandlerInterceptor interceptor = catcher.getHandler();

                // Interceptor doBefore
                // return false, Return to the request page
                if (!interceptor.doBefore(request, response)) {
                    if (log.isDebugEnabled())
                        log.debug("Intercept access request URI: {}, The interception class is: {}", requestURI, interceptor.getClass().getSimpleName());
                    return;
                }

                // Continue to use later: doAfter(), renderCompletion()
                currentInterceptors.add(interceptor);
            }
        }

        // set default character encoding to "utf-8" if encoding is not set:
        if (StringUtils.isEmpty(request.getCharacterEncoding()))
            request.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // static resource
        if (this.resourceRequest.checkStaticResource(request, response))
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
            log.warn("The requested URL (404) Not found: {}", requestURI);
            this.renderError(
                    HttpServletResponse.SC_NOT_FOUND,
                    "The requested URL (404) Not found",
                    Render.NOT_FOUND_HTML,
                    request,
                    response);
            return;
        }

        /*
         * validation request method
         */
        if (!execution.isSupportMethod(request.getMethod())) {
            log.warn("[{}] - HTTP method {} is not supported by this URI, specified as: {}",
                    requestURI, request.getMethod(), execution.methodString());
            this.renderError(
                    HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    String.format("This URL does not support the HTTP method '%s'", request.getMethod()),
                    Render.METHOD_NOT_ALLOWED_HTML,
                    request,
                    response);
            return;
        }

        if (execution.isRequestLog()) {
            log.info("Action is: {}.{}, [{}]",
                    execution.getActionInstance().getClass().getSimpleName(),
                    execution.getMethod().getName(),
                    requestURI);
        }

        // check request is multipart request.
        HttpServletRequest processedRequest = this.checkMultipart(request);
        boolean supportMultipartRequest = processedRequest != request;

        // execute action
        try {
            Action.setActionContext(processedRequest, response);
            Object result = execution.execute(args);

            // Interceptor doAfter
            if (!currentInterceptors.isEmpty()) {
                for (HandlerInterceptor interceptor : currentInterceptors)
                    interceptor.doAfter(processedRequest, response);
            }

            // resolver result
            this.handleResult(result, processedRequest, response);

            // Interceptor renderCompletion
            if (!currentInterceptors.isEmpty())
                for (HandlerInterceptor interceptor : currentInterceptors)
                    interceptor.renderCompletion(processedRequest, response);

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
            if (execution.isRequestLog()) {
                log.info("Used time(ms): {}", DateUtils.getMillis() - begin);
                log.info("End method: {}.{}", execution.getActionInstance().getClass().getSimpleName(), execution.getMethod().getName());
            }

            // Clean up any resources used by a multipart request.
            if (supportMultipartRequest)
                cleanupMultipart(processedRequest);
        }
    }

    /**
     * Convert the request into a multipart request, and make multipart resolver available.
     * <p>If no multipart resolver is set, simply use the existing request.
     *
     * @param request current HTTP request
     * @return the processed request (multipart wrapper if necessary)
     * @see MultipartResolver#resolveMultipart
     */
    protected HttpServletRequest checkMultipart(HttpServletRequest request) throws MultipartException {
        if (Objects.isNull(this.multipartResolver)
                || request.getDispatcherType() != DispatcherType.REQUEST
                || !this.multipartResolver.isMultipart(request)) {
            // return original request
            return request;
        }

        return this.multipartResolver.resolveMultipart(request);
    }

    /**
     * Clean up any resources used by the given multipart request (if any).
     *
     * @param request current HTTP request
     */
    protected void cleanupMultipart(HttpServletRequest request) {
        if (Objects.isNull(this.multipartResolver))
            return;

        this.multipartResolver.cleanupMultipart((MultipartHttpServletRequest) request);
    }


    @Override
    public void handleResult(Object result, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (Objects.isNull(result))
            return;

        Class<?> clazz = result.getClass();
        ConverterFactory converterFactory = ConverterFactory.getInstance();
        if (converterFactory.isConvert(clazz)) {
            Render.stringRender(result.toString()).render(request, response);
            return;
        }

        if (Render.class.isAssignableFrom(clazz)) {
            ((Render) result).render(request, response);
            return;
        }

        if (Collection.class.isAssignableFrom(clazz)
                || Map.class.isAssignableFrom(clazz)
                || Object.class.isAssignableFrom(clazz)) {
            new TextRender(JsonUtils.toJson(result)).render(request, response);
            return;
        }

        throw new ServletException(String.format("Cannot handle result with type '%s'", result.getClass().getName()));
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

}
