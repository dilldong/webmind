package org.mind.framework.web.dispatcher.handler;

import lombok.Getter;
import lombok.Setter;
import org.mind.framework.annotation.Mapping;
import org.mind.framework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.Arrays;


/**
 * Execution contains all information that needed to invocation of method.
 *
 * @author dp
 */
@Getter
public class Execution {

    private final static RequestMethod[] NON_METHODS = {RequestMethod.GET, RequestMethod.POST};

    // Action instance
    private final Object actionInstance;

    // Method instance
    private final Method method;

    // Http request method
    private final RequestMethod[] requestMethods;

    // Method's arguments types
    private final Class<?>[] parameterTypes;

    // Method's need arguments
    private final Object[] arguments;

    // Method's need arguments length
    @Setter
    private int argsNumber;

    // Output request call log on parent
    private final boolean requestLog;

    // Simple one line logging
    private final boolean simpleLogging;

    public Execution(Object actionInstance, Method method, Mapping mapping) {
        this(actionInstance, method, null, mapping);
    }

    public Execution(Object actionInstance, Method method, Object[] arguments, Mapping mapping) {
        this.actionInstance = actionInstance;
        this.method = method;
        this.parameterTypes = method.getParameterTypes();
        this.arguments = arguments;
        this.requestMethods = mapping.method();
        this.requestLog = mapping.requestLog();
        this.simpleLogging = mapping.simpleLogging();
    }

    public Object execute() {
        return ReflectionUtils.invokeMethod(method, actionInstance, arguments);
    }

    public Object execute(Object[] arguments) {
        return ReflectionUtils.invokeMethod(method, actionInstance, arguments);
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
                Arrays.toString(NON_METHODS) :
                Arrays.toString(requestMethods);
    }
}