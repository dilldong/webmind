package org.mind.framework.dispatcher.handler;

import org.mind.framework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.Arrays;


/**
 * Execution contains all information that needed to invocation of method.
 *
 * @author dp
 */
public class Execution {

    // Action instance
    private final Object actionInstance;

    // Method instance
    private final Method method;

    // Http request method
    private RequestMethod[] requestMethods;

    private final static RequestMethod[] nonMethods = {RequestMethod.GET, RequestMethod.POST};

    // Method's arguments types
    private final Class<?>[] parameterTypes;

    // Method's need arguments
    private final Object[] arguments;

    // Method's need arguments length
    private int argsNumber;

    // Output request call log on parent
    private boolean requestLog;

    public Execution(Object actionInstance, Method method, RequestMethod[] requestMethods, boolean requestLog) {
        this(actionInstance, method, null, requestMethods, requestLog);
    }

    public Execution(Object actionInstance, Method method, Object[] arguments, RequestMethod[] requestMethods, boolean requestLog) {
        this.actionInstance = actionInstance;
        this.method = method;
        this.parameterTypes = method.getParameterTypes();
        this.arguments = arguments;
        this.requestMethods = requestMethods;
        this.requestLog = requestLog;
    }

    public Object execute() {
        return ReflectionUtils.invokeMethod(method, actionInstance, arguments);
    }

    public Object execute(Object[] arguments) {
        return ReflectionUtils.invokeMethod(method, actionInstance, arguments);
    }

    protected Object getActionInstance() {
        return actionInstance;
    }

    protected Method getMethod() {
        return method;
    }

    protected Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    protected Object[] getArguments() {
        return arguments;
    }

    protected int getArgsNumber() {
        return argsNumber;
    }

    protected void setArgsNumber(int argsNumber) {
        this.argsNumber = argsNumber;
    }

    public RequestMethod[] getRequestMethods() {
        return requestMethods;
    }

    public void setRequestMethods(RequestMethod[] requestMethods) {
        this.requestMethods = requestMethods;
    }

    public boolean isRequestLog() {
        return requestLog;
    }

    public boolean isSupportMethod(String method) {
        if (requestMethods == null || requestMethods.length == 0)
            return method.equals(RequestMethod.GET.name()) || method.equals(RequestMethod.POST.name());

        for (RequestMethod m : requestMethods)
            if (method.equals(m.name()))
                return true;

        return false;
    }

    public String methodString() {
        return requestMethods == null || requestMethods.length == 0 ?
                Arrays.toString(nonMethods) :
                Arrays.toString(requestMethods);
    }
}