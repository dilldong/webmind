package org.mind.framework.web.dispatcher.handler;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.ArrayUtils;
import org.mind.framework.annotation.AsyncAction;
import org.mind.framework.annotation.Mapping;
import org.mind.framework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


/**
 * Execution contains all information that needed to invocation of method.
 *
 * @author dp
 */
@Getter
public class Execution {
    // Key: regex
    private final Set<String> regex;

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

    // Async action
    private final AsyncAction async;

    public Execution(Object actionInstance, Method method, Mapping mapping) {
        this(actionInstance, method, null, mapping);
    }

    public Execution(Object actionInstance, Method method, Object[] arguments, Mapping mapping) {
        this.regex = new HashSet<>();
        this.actionInstance = actionInstance;
        this.method = method;
        this.parameterTypes = method.getParameterTypes();
        this.arguments = arguments;
        this.requestMethods = mapping.method();
        this.requestLog = mapping.requestLog();
        this.simpleLogging = mapping.simpleLogging();
        this.async = mapping.async();
    }

    public Object execute() {
        return ReflectionUtils.invokeMethod(method, actionInstance, arguments);
    }

    public Object execute(Object[] arguments) {
        return ReflectionUtils.invokeMethod(method, actionInstance, arguments);
    }

    public void addRegex(String regex) {
        this.regex.add(regex);
    }

    public boolean isSupportMethod(String method) {
        if (ArrayUtils.isEmpty(requestMethods))
            return false;

        for (RequestMethod m : requestMethods)
            if (method.equals(m.name()))
                return true;

        return false;
    }

    public String methodString() {
        return ArrayUtils.isEmpty(requestMethods) ? null : Arrays.toString(requestMethods);
    }
}