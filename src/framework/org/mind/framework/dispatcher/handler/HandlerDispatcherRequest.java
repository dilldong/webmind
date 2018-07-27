package org.mind.framework.dispatcher.handler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mind.framework.Action;
import org.mind.framework.annotation.Interceptor;
import org.mind.framework.annotation.Mapping;
import org.mind.framework.dispatcher.support.ConverterFactory;
import org.mind.framework.exception.NotSupportedException;
import org.mind.framework.interceptor.AbstractHandlerInterceptor;
import org.mind.framework.interceptor.HandlerInterceptor;
import org.mind.framework.renderer.JavaScriptRender;
import org.mind.framework.renderer.Render;
import org.mind.framework.renderer.TextRender;
import org.mind.framework.util.DateFormatUtils;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.util.UriPath;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
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

    private static final Log log = LogFactory.getLog(HandlerDispatcherRequest.class);

    private ConverterFactory converter;
    private ServletContext servletContext;
    private ServletConfig servletConfig;

    private Map<String, Execution> actions;// URI regex mapping object
    private List<String> urisRegex; // URI regex list

    // interceptor mapping
    private Map<String, HandlerInterceptor> interceptorMap;
    private List<String> interceptorsRegex;

    // static resource decl.
    private String resStr;

    // static resource handler.
    private HandlerResult resourceHandler;

    // default not support multipartrequest.
    private boolean supportMultipartRequest = false;

    public HandlerDispatcherRequest(ServletConfig config) {
        this.servletConfig = config;
        this.servletContext = config.getServletContext();
    }

    @Override
    public void init(List<Object> beans) {
        this.converter = ConverterFactory.getInstance();
        this.urisRegex = new ArrayList<String>();
        this.interceptorsRegex = new ArrayList<String>();
        this.resourceHandler = new ResourceHttpRequest(this.servletConfig);

        // load web application static resource strs.
        this.resStr = this.servletConfig.getInitParameter("resource");
        if (log.isDebugEnabled())
            log.debug("resource suffix: " + resStr);


        // init Action Maps, support hot load, so used java.util.concurrent.ConcurrentHashMap.
        this.actions = new HashMap<String, Execution>() {
            private static final long serialVersionUID = 64639524551549449L;
            private String regexKey;

            @Override
            public Execution put(String key, Execution value) {
                regexKey = MatcherUtils.convertURI(key);// convert URI to Regex
                if (this.containsKey(regexKey))
                    throw new IllegalArgumentException("URI mapping is a globally unique, and can not be repeated: " + key);

                value.setArgsNumber(MatcherUtils.checkCount(key, MatcherUtils.URI_PARAM_MATCH));// find args number
                urisRegex.add(regexKey);// add List
                return super.put(regexKey, value);
            }
        };

        // init Interceptor
        this.interceptorMap = new HashMap<String, HandlerInterceptor>() {
            private String regexKey;

            @Override
            public HandlerInterceptor put(String key, HandlerInterceptor value) {
                regexKey = MatcherUtils.convertURI(key);
                if (this.containsKey(regexKey))
                    throw new IllegalArgumentException("URI mapping is a globally unique, and can not be repeated: " + key);

                interceptorsRegex.add(regexKey);
                return super.put(regexKey, value);
            }
        };

        /*
         * init Action by Spring/Guice Container and create on the URI mapping relationship
         */
        for (Object bean : beans) {
            this.loadAction(bean);
        }

        /*
         * detect multipart support:
         */
        try {
            Class.forName("org.apache.commons.fileupload.servlet.ServletFileUpload");
            log.info("using MultipartRequest to handle multipart http request.");
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

        actions = null;
        urisRegex = null;
    }

    @Override
    public void processor(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        long begin = DateFormatUtils.getTimeMillis();
        String path = UriPath.get(request);

        // set default character encoding to "utf-8" if encoding is not set:
        if (request.getCharacterEncoding() == null)
            request.setCharacterEncoding("UTF-8");

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

        if (log.isInfoEnabled()) {
            log.info("From path: " + path);
        }

        this.processNoCache(request, response);

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

        if (log.isInfoEnabled()) {
            log.info("Action is: " +
                    execution.getActionInstance().getClass().getSimpleName()
                    + "." +
                    execution.getMethod().getName());
        }

        // currently request is multipart request.
        if (this.supportMultipartRequest &&
                MultipartHttpServletRequest.isMultipartRequest(request)) {
            request = MultipartHttpServletRequest.getInstance(request);
        }

        /*
         * gloable interceptor exchain
         *
         * interceptor.doBefore(execution, request, response);
         * doAfter();
         * renderCompletion();
         */
        List<HandlerInterceptor> interceptors = new ArrayList<>();
        if (interceptorsRegex != null) {
            HandlerInterceptor interceptor;
            for (String regex : interceptorsRegex) {
                matcher = MatcherUtils.matcher(path, regex, MatcherUtils.DEFAULT_EQ);
                if (!matcher.matches())// not-match
                    continue;

                interceptor = interceptorMap.get(regex);

                // Interceptor doBefore
                // return false, Return to the request page
                if (!interceptor.doBefore(request, response))
                    return;

                interceptors.add(interceptor);
            }
        }

        // execute action
        try {
            Action.setActionContext(servletContext, request, response);
            Object result = execution.execute(args);

            // Interceptor doAfter
            if (!interceptors.isEmpty()) {
                int size = interceptors.size();
                for (int i = size - 1; i >= 0; i--) {
                    interceptors.get(i).doAfter(request, response);
                }
            }
            this.handleResult(result, request, response);

            // Interceptor renderCompletion
            if (!interceptors.isEmpty()) {
                int size = interceptors.size();
                for (int i = size - 1; i >= 0; i--) {
                    interceptors.get(i).renderCompletion(request, response);
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
            if (log.isInfoEnabled()) {
                log.info("Used time(ms): " + (DateFormatUtils.getTimeMillis() - begin));
                log.info("End method: " +
                        execution.getActionInstance().getClass().getSimpleName()
                        + "." +
                        execution.getMethod().getName());
            }
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
                this.doForward(str.substring("forward:".length()), request, response);
                return;
            }

            if (str.startsWith("redirect:")) {
                this.doRedirect(str.substring("redirect:".length()), request, response);
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
        throw new ServletException("Cannot handle result with type '" + result.getClass().getName());

    }

    protected void doForward(
            String uri,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException, ServletException {

        if (log.isInfoEnabled())
            log.info("Forward path: " + uri);

        // 	Unwrap the multipart request, if there is one.
//	        if (request instanceof MultipartRequestWrapper) {
//	            request = ((MultipartRequestWrapper) request).getRequest();
//	        }

        RequestDispatcher rd = request.getRequestDispatcher(uri);
        if (rd == null) {
            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Not forward path: " + uri);
            return;
        }

        rd.forward(request, response);
    }

    protected void doRedirect(
            String uri,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException, ServletException {

        if (uri.startsWith("/"))
            uri = request.getContextPath() + uri;

        if (log.isInfoEnabled())
            log.info("Redirect path: " + uri);

        response.sendRedirect(response.encodeRedirectURL(uri));
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
    protected void loadAction(Object bean) {
        Class<? extends Object> clazz = bean.getClass();

        // if Interceptor
        if (clazz.isAnnotationPresent(Interceptor.class)) {
            if (AbstractHandlerInterceptor.class.isAssignableFrom(clazz) || HandlerInterceptor.class.isAssignableFrom(clazz)) {
                Interceptor interceptor = clazz.getAnnotation(Interceptor.class);
                interceptorMap.put(interceptor.value(), (HandlerInterceptor) bean);
                if (log.isInfoEnabled())
                    log.info("Loaded Interceptor: " + interceptor.value());
            } else {
                log.error("The interceptor needs to implement the HandlerInterceptor interface or inherit the AbstractHandlerInterceptor class.");
                throw new NotSupportedException("The interceptor needs to implement the HandlerInterceptor interface or inherit the AbstractHandlerInterceptor class.");
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

        if (log.isInfoEnabled() && sb.length() > 0)
            log.info("Loaded URI mapping: " + sb.substring(0, sb.length() - 1));
    }

    private boolean isMappingMethod(Method method) {
        Mapping mapping = method.getAnnotation(Mapping.class);
        if (mapping == null)
            return false;

        if (mapping.value().trim().length() == 0) {
            log.warn("Invalid Action method '" + method.toGenericString() + "', URI mapping value cannot be empty.");
            return false;
        }

        if (Modifier.isStatic(method.getModifiers())) {
            log.warn("Invalid Action method '" + method.toGenericString() + "' is static.");
            return false;
        }

        Class<?>[] argTypes = method.getParameterTypes();
        for (Class<?> argType : argTypes) {
            if (!converter.isConvert(argType)) {
                log.warn("Invalid Action method '" + method.toGenericString() + "' unsupported parameter type '" + argType.getName() + "'.");
                return false;
            }
        }

        Class<?> retType = method.getReturnType();
        if (void.class.equals(retType)
                || String.class.equals(retType)
                || Render.class.isAssignableFrom(retType)) {
            return true;
        }

        log.warn("Unsupported Action method '" + method.toGenericString() + "'.");
        return false;
    }

}
