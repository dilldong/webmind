package org.mind.framework.dispatcher.handler;

import org.apache.commons.lang.StringUtils;
import org.mind.framework.Action;
import org.mind.framework.annotation.Interceptor;
import org.mind.framework.annotation.Mapping;
import org.mind.framework.dispatcher.support.Catcher;
import org.mind.framework.dispatcher.support.ConverterFactory;
import org.mind.framework.interceptor.AbstractHandlerInterceptor;
import org.mind.framework.interceptor.HandlerInterceptor;
import org.mind.framework.renderer.JavaScriptRender;
import org.mind.framework.renderer.NullRender;
import org.mind.framework.renderer.Render;
import org.mind.framework.renderer.TextRender;
import org.mind.framework.util.DateFormatUtils;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.util.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;


/**
 * Dispatcher handles ALL requests from clients, and dispatches to appropriate
 * handler to handle each request.
 *
 * @author dp
 */
public class HandlerDispatcherRequest implements HandlerRequest, HandlerResult {

    private static final Logger log = LoggerFactory.getLogger(HandlerDispatcherRequest.class);

    private ConverterFactory converter;
    private ServletContext servletContext;
    private ServletConfig servletConfig;

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
        this.urisRegex = new ArrayList<String>();
        this.interceptorsCatcher = new ArrayList<Catcher>();
        this.resourceHandler = new ResourceHttpRequest(this.servletConfig);

        // load web application static resource strs.
        this.resStr = this.servletConfig.getInitParameter("resource");
        log.debug("resource suffix: {}", resStr);

        // init Action Maps, support hot load, so used java.util.concurrent.ConcurrentHashMap.
        this.actions = new HashMap<String, Execution>() {
            private static final long serialVersionUID = 64639524551549449L;
            private String regexKey;

            @Override
            public Execution put(String key, Execution value) {
                regexKey = MatcherUtils.convertURI(key);// convert URI to Regex
                if (this.containsKey(regexKey))
                    throw new IllegalArgumentException(String.format("URI mapping is a globally unique, and can not be repeated: %s", key));

                value.setArgsNumber(MatcherUtils.checkCount(key, MatcherUtils.URI_PARAM_MATCH));// find args number
                urisRegex.add(regexKey);// add List
                return super.put(regexKey, value);
            }
        };


        /*
         * init Action by Spring/Guice Container and create on the URI mapping relationship
         */
        for (Object bean : beans) {
            this.loadAction(bean);
        }

        // Interceptor forward sorting
        Collections.sort(interceptorsCatcher);
        log.info("Interceptors: {}", Arrays.toString(interceptorsCatcher.toArray(new Catcher[]{})));

        /*
         * detect multipart support:
         */
        try {
            Class.forName("org.apache.commons.fileupload.servlet.ServletFileUpload");
            if (log.isInfoEnabled())
                log.info("Using MultipartRequest to handle multipart http request.");

            this.supportMultipartRequest = true;
        } catch (ClassNotFoundException e) {
            log.error("MultipartRequest not found. Multipart http request can not be handled.");
        }
    }

    @Override
    public void destroy() {
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
    public void processor(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        long begin = DateFormatUtils.getTimeMillis();
        String path = UriPath.get(request);
        Matcher matcher;

        /*
         * Gloable interceptor exchain
         *
         * interceptor.doBefore(execution, request, response);
         * doAfter();
         * renderCompletion();
         */
        List<HandlerInterceptor> currentInterceptors = new ArrayList<>();
        if (interceptorsCatcher != null) {
            for (Catcher catcher : interceptorsCatcher) {
                matcher = MatcherUtils.matcher(path, catcher.getInterceptorRegex(), MatcherUtils.DEFAULT_EQ);
                if (!matcher.matches())// not-match
                    continue;

                HandlerInterceptor interceptor = catcher.getHander();

                // Interceptor doBefore
                // return false, Return to the request page
                if (!interceptor.doBefore(request, response)) {
                    log.debug("Intercept access request URI: {}, The interception class is: {}", path, interceptor.getClass().getSimpleName());
                    return;
                }

                // Continue to use later: doAfter(), renderCompletion()
                currentInterceptors.add(interceptor);
            }
        }

        // set default character encoding to "utf-8" if encoding is not set:
        if (request.getCharacterEncoding() == null)
            request.setCharacterEncoding("UTF-8");

        // static resource
        int subIndex = path.lastIndexOf(".");
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

        log.info("From path: {}", path);

        this.processNoCache(request, response);

        /*
         * find and process action
         */
        Execution execution = null;
        Object[] args = null;

        for (String regex : this.urisRegex) {
            matcher = MatcherUtils.matcher(path, regex, MatcherUtils.DEFAULT_EQ);
            if (!matcher.matches())// not-match
                continue;

            execution = this.actions.get(regex);
            int number = execution.getArgsNumber();
            if (number > 0) {
                args = new Object[number];

                /*
                 * Fetch request parameters in the URI
                 */
                Class<?> type;
                for (int i = 0; i < number; i++) {
                    type = execution.getParameterTypes()[i];
                    if (String.class.equals(type))
                        args[i] = matcher.group(i + 1);// segmentation fetch
                    else
                        args[i] = this.converter.convert(type, matcher.group(i + 1));
                }
            }
            break;
        }

        /*
         * Status code (404) indicating that the requested resource is not
         * available.
         */
        if (execution == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "(404) Not found.");
            return;
        }

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
                for (HandlerInterceptor interceptor : currentInterceptors) {
                    interceptor.doAfter(request, response);
                }
            }
            this.handleResult(result, request, response);

            // Interceptor renderCompletion
            if (!currentInterceptors.isEmpty()) {
                for (HandlerInterceptor interceptor : currentInterceptors) {
                    interceptor.renderCompletion(request, response);
                }
            }

        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            Throwable c = e.getCause();
            if (c instanceof IOException)
                throw new IOException(c.getMessage(), c);
            else
                throw new ServletException(c.getMessage(), c);// other exception throws with ServletException.
        } finally {
            Action.removeActionContext();
            log.info("Used time(ms): {}", (DateFormatUtils.getTimeMillis() - begin));
            log.info("End method: {}.{}", execution.getActionInstance().getClass().getSimpleName(), execution.getMethod().getName());
        }
    }

    public void handleResult(Object result, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (result == null)
            return;

        if (result instanceof Render) {
            Render render = (Render) result;
            render.render(request, response);
            return;
        }

        if (result instanceof String) {
            String str = (String) result;

            if (str.startsWith("forward:")) {
                new NullRender(str.substring("forward:".length()), NullRender.NullRenderType.FORWARD).render(request, response);
                return;
            }

            if (str.startsWith("redirect:")) {
                new NullRender(str.substring("redirect:".length()), NullRender.NullRenderType.REDIRECT).render(request, response);
                return;
            }

            if (str.startsWith("script:")) {
                String script = str.substring("script:".length());
                new JavaScriptRender(script).render(request, response);
                return;
            }

            new TextRender(str).render(request, response);
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
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     */
    protected void processNoCache(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Pragma", "No-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0L);
    }


    /**
     * Initialize all Action objects, and {@link Mapping} increased URI mapping.
     *
     * @param bean
     */
    protected void loadAction(Object bean) throws ServletException {
        Class<? extends Object> clazz = bean.getClass();

        // if Interceptor
        if (clazz.isAnnotationPresent(Interceptor.class)) {
            if (AbstractHandlerInterceptor.class.isAssignableFrom(clazz) || HandlerInterceptor.class.isAssignableFrom(clazz)) {
                Interceptor interceptor = clazz.getAnnotation(Interceptor.class);

                interceptorsCatcher.add(new Catcher(interceptor, (HandlerInterceptor) bean));
                log.debug("Loaded Interceptor: {}", interceptor.value());
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
                    new Execution(bean, method));

            sb.append(mapping.value()).append(", ");
        }

        if (sb.length() > 0)
            log.info("Loaded URI mapping: {}", sb.substring(0, sb.length() - 1));
    }

    private boolean isMappingMethod(Method method) {
        Mapping mapping = method.getAnnotation(Mapping.class);
        if (mapping == null)
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

        Class<?> retType = method.getReturnType();
        if (void.class.equals(retType)
                || String.class.equals(retType)
                || Render.class.isAssignableFrom(retType)) {
            return true;
        }

        log.warn("Unsupported Action method '{}'.", method.toGenericString());
        return false;
    }

}
