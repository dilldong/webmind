package org.mind.framework.exception;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * checked 异常基类，用于描述一种异常情况的发生，并且这种异常情况是需要捕获进行处理
 * 
 * @author dongping
 */
public class BaseException extends Exception {
	
	private static final long serialVersionUID = -846923593612400497L;

	public final static int ERR_INF = 1;

	public final static int ERR_APP = 2;

	public final static int ERR_SYS = 3;

	protected int errLevel = ERR_INF;

	protected StringBuffer backStacks = new StringBuffer();
	
	public BaseException(){
		super();
	}
	
	public BaseException(Throwable cause){
		super(cause);
	}

	public BaseException(String msg) {
		super(msg);
	}

	public BaseException(String msg, int level) {
		super(msg);
		errLevel = level;
	}

	public BaseException(String msg, int level, BaseException e) {
		super(msg);
		errLevel = level;
		backStacks.append(getStackTrace(e));
	}

	public BaseException(String msg, int level, Throwable e) {
		super(msg);
		errLevel = level;
		backStacks.append(getStackTrace(e));
	}

	public BaseException(String msg, BaseException e) {
		super(msg);
		backStacks.append(getStackTrace(e));
	}

	public BaseException(String msg, Throwable e) {
		super(msg);
		backStacks.append(getStackTrace(e));
	}

	public int getErrLevel() {
		return errLevel;
	}

	public final String getBackStacks() {
		return backStacks.toString();
	}

	public final static String getStackTrace(Throwable e) {
		StringWriter sw = new StringWriter();
		PrintWriter out = new PrintWriter(sw);
		e.printStackTrace(out);
		return sw.toString();
	}

	public final static String getStackTrace(BaseException e) {
		StringWriter sw = new StringWriter();
		PrintWriter out = new PrintWriter(sw);
		if (e.backStacks != null)
			out.println(e.backStacks);
		e.printStackTrace(out);
		return sw.toString();
	}

	public void printStackTrace(PrintStream out) {
		if (backStacks != null)
			out.println(backStacks);
		super.printStackTrace(out);
	}

	public void printStackTrace(PrintWriter out) {
		if (backStacks != null)
			out.println(backStacks);
		super.printStackTrace(out);
	}

	public void printStackTrace() {
		printStackTrace(System.err);
	}
}
