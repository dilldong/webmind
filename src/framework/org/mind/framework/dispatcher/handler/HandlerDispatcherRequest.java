package org.mind.framework.dispatcher.handler;

import org.apache.commons.lang3.StringUtils;
import org.mind.framework.Action;
import org.mind.framework.annotation.Interceptor;
import org.mind.framework.annotation.Mapping;
import org.mind.framework.dispatcher.support.Catcher;
import org.mind.framework.dispatcher.support.ConverterFactory;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.interceptor.AbstractHandlerInterceptor;
import org.mind.framework.interceptor.HandlerInterceptor;
import org.mind.framework.renderer.Render;
import org.mind.framework.renderer.TextRender;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.util.DateFormatUtils;
import org.mind.framework.util.HttpUtils;
import org.mind.framework.util.IOUtils;
import org.mind.framework.util.JsonUtils;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.util.ViewResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
public class HandlerDispatcherRequest implements HandlerRequest, HandlerResult {

    private static final Logger log = LoggerFactory.getLogger("RequestHandler");

    private ConverterFactory converter;
    private final ServletContext servletContext;
    private final ServletConfig servletConfig;

    private Map<String, Execution> actions;// URI regex mapping object
    private List<String> urisRegex; // URI regex list

    // interceptor mapping
    private List<Catcher> interceptorsCatcher;

    // static resource decl.
    private String resStr;

    // static resource handler.
    private HandlerResult resourceHandler;

    // default not support multipart request.
    private boolean supportMultipartRequest = false;

    public HandlerDispatcherRequest(ServletConfig config) {
        this.servletConfig = config;
        this.servletContext = config.getServletContext();
    }

    @Override
    public void init(List<Object> beans) throws ServletException {
        this.converter = ConverterFactory.getInstance();
        this.urisRegex = new ArrayList<>();
        this.interceptorsCatcher = new ArrayList<>();
        this.resourceHandler = new HandlerResourceRequest(this.servletConfig);

        // load web application static resource strs.
        this.resStr = this.servletConfig.getInitParameter("resource");
        if(log.isDebugEnabled())
            log.debug("resource suffix: {}", resStr);

        // init Action Maps, support hot load, so used java.util.concurrent.ConcurrentHashMap.
        this.actions = new HashMap<String, Execution>() {
            @Override
            public Execution put(String key, Execution value) {
                String regexKey = MatcherUtils.convertURI(key);// convert URI to Regex
                if (this.containsKey(regexKey)) {
                    log.error("URI mapping is a globally unique, and can not be repeated: {}", key);
                    throw new IllegalArgumentException(String.format("URI mapping is a globally unique, and can not be repeated: %s", key));
                }

                value.setArgsNumber(MatcherUtils.checkCount(key, MatcherUtils.URI_PARAM_PATTERN));// find args number
                urisRegex.add(regexKey);// add List
                return super.put(regexKey, value);
            }
        };


        /*
         * init Action by Spring/Guice Container and create on the URI mapping relationship
         */
        for (Object bean : beans) {
            this.loadBean(bean);
        }

        // Interceptor forward sorting
        if (interceptorsCatcher.size() > 1)
            Collections.sort(interceptorsCatcher);

        if (log.isInfoEnabled())
            interceptorsCatcher.forEach(catcher -> log.info("Interceptors: {}", catcher.toString()));

        /*
         * detect multipart support:
         */
        try {
            ClassUtils.getClass("org.apache.commons.fileupload.servlet.ServletFileUpload");
            if (log.isInfoEnabled())
                log.info("Using MultipartRequest to handle multipart http request.");

            this.supportMultipartRequest = true;
        } catch (ClassNotFoundException e) {
            log.warn("MultipartRequest not found. Multipart http request can not be handled.");
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
        long begin = DateFormatUtils.getMillis();
        final String path = HttpUtils.getURI(request);

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
                if (!catcher.matchOne(path, MatcherUtils.DEFAULT_EQ))// not-match
                    continue;

                HandlerInterceptor interceptor = catcher.getHander();

                // Interceptor doBefore
                // return false, Return to the request page
                if (!interceptor.doBefore(request, response)) {
                    if (log.isDebugEnabled())
                        log.debug("Intercept access request URI: {}, The interception class is: {}", path, interceptor.getClass().getSimpleName());
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
        int subIndex = path.lastIndexOf(IOUtils.DOT_SEPARATOR);
        if (subIndex != -1) {
            String suffix = path.substring(subIndex + 1);

            // return true is http request static resource.
            if (MatcherUtils.matcher(
                    suffix,
                    this.resStr,
                    MatcherUtils.IGNORECASE_EQ).matches()) {

                this.resourceHandler.handleResult(path, request, response);
                return;
            }
        }

        // set response no-cache
        this.processNoCache(response);

        /*
         * find and process action
         */
        Execution execution = null;
        Object[] args = null;
        Matcher matcher;

        for (String regex : this.urisRegex) {
            matcher = MatcherUtils.matcher(path, regex, MatcherUtils.DEFAULT_EQ);
            if (!matcher.matches())// not-match
                continue;

            execution = this.actions.get(regex);
            int number = execution.getArgsNumber();
            if (number > 0) {
                args = new Object[number];

                // Fetch request parameters in the URI
                Class<?> type;
                for (int i = 0; i < number; ++i) {
                    try {
                        type = execution.getParameterTypes()[i];
                    } catch (ArrayIndexOutOfBoundsException e) {
                        log.warn("{}, exception: {} - {}", path, e.getClass().getName(), e.getMessage());
                        throw new IllegalArgumentException("Method is missing URL parameter");
                    }

                    if (String.class.getName().equals(type.getName()))
                        args[i] = matcher.group(i + 1);// segmentation fetch
                    else {
                        try {
                            args[i] = this.converter.convert(type, matcher.group(i + 1));
                        } catch (NumberFormatException | NullPointerException e) {
                            log.warn("{}, exception: {} - {}", path, e.getClass().getName(), e.getMessage());
                            throw new IllegalArgumentException("URL parameters was incorrect");
                        }
                    }
                }
            }
            break;
        }

        /*
         * Status code (404) indicating that the requested resource is not available.
         */
        if (Objects.isNull(execution)) {
            log.warn("The requested URL (404) Not found: {}", path);
            this.sendError(
                    HttpServletResponse.SC_NOT_FOUND,
                    "The requested URL (404) Not found",
                    Render.NOT_FOUND_HTML,
                    request,
                    response);
            return;
        }

        if (execution.isRequestLog())
            log.info("From path: {}", path);

        /*
         * validation request method
         */
        if (!execution.isSupportMethod(request.getMethod())) {
            log.warn("HTTP method {} is not supported by this URI, specified as: {}",
                    request.getMethod(), execution.methodString());
            this.sendError(
                    HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    String.format("This URL does not support the HTTP method '%s'", request.getMethod()),
                    Render.METHOD_NOT_ALLOWED_HTML,
                    request,
                    response);
            return;
        }

        if (execution.isRequestLog())
            log.info("Action is: {}.{}", execution.getActionInstance().getClass().getSimpleName(), execution.getMethod().getName());

        // currently request is multipart request.
        if (this.supportMultipartRequest &&
                MultipartHttpServletRequest.isMultipartRequest(request)) {
            request = MultipartHttpServletRequest.getInstance(request);
        }

        // execute action
        try {
            Action.setActionContext(servletContext, request, response);
            Object result = execution.execute(args);

            // Interceptor doAfter
            if (!currentInterceptors.isEmpty()) {
                for (HandlerInterceptor interceptor : currentInterceptors)
                    interceptor.doAfter(request, response);
            }

            // resolver result
            this.handleResult(result, request, response);

            // Interceptor renderCompletion
            if (!currentInterceptors.isEmpty())
                for (HandlerInterceptor interceptor : currentInterceptors)
                    interceptor.renderCompletion(request, response);

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
                log.info("Used time(ms): {}", DateFormatUtils.getMillis() - begin);
                log.info("End method: {}.{}", execution.getActionInstance().getClass().getSimpleName(), execution.getMethod().getName());
            }
        }
    }

    @Override
    public void handleResult(Object result, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (Objects.isNull(result))
            return;

        Class<? extends Object> clazz = result.getClass();
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


    /**
     * Initialize all Bean objects, and {@link Mapping} increased URI mapping.
     *
     * @param bean
     */
    protected void loadBean(Object bean) throws ServletException {
        Class<? extends Object> clazz = bean.getClass();

        // if Interceptor
        if (clazz.isAnnotationPresent(Interceptor.class)) {
            if (AbstractHandlerInterceptor.class.isAssignableFrom(clazz) || HandlerInterceptor.class.isAssignableFrom(clazz)) {
                Interceptor interceptor = clazz.getAnnotation(Interceptor.class);

                interceptorsCatcher.add(new Catcher(interceptor, (HandlerInterceptor) bean));
                log.info("Loaded Interceptor: {}", Arrays.toString(interceptor.value()));
            } else {
                throw new ServletException("The interceptor needs to implement the HandlerInterceptor interface or inherit the AbstractHandlerInterceptor class.");
            }
            return;
        }

        StringBuilder sb = new StringBuilder();
        // Mapping
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (!this.isMappingMethod(method))
                continue;

            Mapping mapping = method.getAnnotation(Mapping.class);
            this.actions.put(
                    mapping.value(),
                    new Execution(bean, method, mapping.method(), mapping.requestLog()));

            sb.append(mapping.value()).append(", ");
        }

        if (sb.length() > 0)
            log.info("Loaded URI mapping: {}", StringUtils.substringBeforeLast(sb.toString(), ","));
    }

    private boolean isMappingMethod(Method method) {
        Mapping mapping = method.getAnnotation(Mapping.class);
        if (Objects.isNull(mapping))
            return false;

        if (StringUtils.trimToEmpty(mapping.value()).length() == 0) {
            log.warn("Invalid Action method '{}', URI mapping value cannot be empty.", method.toGenericString());
            return false;
        }

        if (Modifier.isStatic(method.getModifiers())) {
            log.warn("Invalid Action method '{}' is static.", method.toGenericString());
            return false;
        }

        Class<?>[] argTypes = method.getParameterTypes();
        for (Class<?> argType : argTypes) {
            if (!converter.isConvert(argType)) {
                log.warn("Invalid Action method '{}' unsupported parameter type '{}'.", method.toGenericString(), argType.getName());
                return false;
            }
        }

        return true;
    }

    private void sendError(int statusCode, String jsonMessage, String htmlMessage, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setStatus(statusCode);
        // json body
        String contentType = request.getContentType();
        if (StringUtils.isNotEmpty(contentType) && contentType.startsWith(MediaType.APPLICATION_JSON_VALUE))
            ViewResolver.text(ViewResolver.<String>response(statusCode, jsonMessage).toJson())
                    .render(request, response);
        else
            ViewResolver.text(htmlMessage).render(request, response);

    }

}
