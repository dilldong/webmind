package org.mind.framework.dispatcher.handler;

import java.lang.reflect.Method;

import org.mind.framework.util.ReflectionUtils;


/**
 * Execution contains all information that needed to invocation of method.
 * 
 * @author dp
 *
 */
public class Execution {

	// Action instance
	private Object actionInstance;
	
	// Method instance
	private Method method;
	
	// Method's arguments types
	private Class<?>[] parameterTypes;
	
	// Method's need arguments
	private Object[] arguments;
	
	// Method's need arguments length
	private int argsNumber;
	
	public Execution(Object actionInstance, Method method) {
		this(actionInstance, method, null);
	}

	public Execution(Object actionInstance, Method method, Object[] arguments) {
		this.actionInstance = actionInstance;
		this.method = method;
		this.parameterTypes = method.getParameterTypes();
		this.arguments = arguments;
	}

	
	public Object execute(){
		return 
				ReflectionUtils.invokeMethod(method, actionInstance, arguments);
	}
	
	public Object execute(Object[] arguments){
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
	
	
}