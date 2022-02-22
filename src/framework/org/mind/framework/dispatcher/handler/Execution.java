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
    private Object actionInstance;

    // Method instance
    private Method method;

    // Http request method
    private RequestMethod[] requestMethods;

    // Method's arguments types
    private Class<?>[] parameterTypes;

    // Method's need arguments
    private Object[] arguments;

    // Method's need arguments length
    private int argsNumber;

    public Execution(Object actionInstance, Method method, RequestMethod[] requestMethods) {
        this(actionInstance, method, null, requestMethods);
    }

    public Execution(Object actionInstance, Method method, Object[] arguments, RequestMethod[] requestMethods) {
        this.actionInstance = actionInstance;
        this.method = method;
        this.parameterTypes = method.getParameterTypes();
        this.arguments = arguments;
        this.requestMethods = requestMethods;
    }


    public Object execute() {
        return
                ReflectionUtils.invokeMethod(method, actionInstance, arguments);
    }

    public Object execute(Object[] arguments) {
        return
                ReflectionUtils.invokeMethod(method, actionInstance, arguments);
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

    public boolean isSupportMethod(String method) {
        if (requestMethods == null || requestMethods.length == 0)
            return method.equalsIgnoreCase(RequestMethod.GET.name()) || method.equalsIgnoreCase(RequestMethod.POST.name());

        for (RequestMethod m : requestMethods)
            if (method.equalsIgnoreCase(m.name()))
                return true;

        return false;
    }

    public String requestMethodsString() {
        return requestMethods == null ? "" : Arrays.toString(requestMethods);
    }
}