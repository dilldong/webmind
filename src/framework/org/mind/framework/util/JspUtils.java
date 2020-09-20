package org.mind.framework.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.Tag;

/**
 * 
 * @author dongping
 */
public final class JspUtils extends ResponseUtils {

	private static Logger logger = LoggerFactory.getLogger(JspUtils.class);
	
	private static final ThreadLocal<JspUtils> responseInstance = 
		new ThreadLocal<JspUtils>();
	
	private transient final Lock lock = new ReentrantLock();
	
	private StringBuilder msgBuffer;

	private JspUtils() {
		msgBuffer = new StringBuilder(64);
	}
	
	public static JspUtils getInstance(){
		JspUtils thisUtil = responseInstance.get();
		if(thisUtil == null){// dcl
			synchronized (JspUtils.class) {
				if(thisUtil == null){
					thisUtil = new JspUtils();
					responseInstance.set(thisUtil);
				}
			}
		}
		return thisUtil;
	}
	
	public JspUtils append(Object msg){
		final Lock lock = this.lock;
		lock.lock();
		try{
			msgBuffer.append(msg);
			return this;
		}finally{
			lock.unlock();
		}
	}
	
	public StringBuilder getBuffer(){
		final Lock lock = this.lock;
		lock.lock();
		try{
			return this.msgBuffer;
		}finally{
			lock.unlock();
		}
	}
	
	public void print(HttpServletResponse response){
		final Lock lock = this.lock;
		lock.lock();
		try{
			print(response, this.msgBuffer.toString());
			this.msgBuffer = null;
		}finally{
			lock.unlock();
		}
	}
	
	/**
	 * PrintWriter输出
	 * @param out PrintWriter
	 * @author dongping
	 */
	public void print(PrintWriter out){
		final Lock lock = this.lock;
		lock.lock();
		try{
			print(out, this.msgBuffer.toString());
			this.msgBuffer.delete(0, msgBuffer.length());
		}finally{
			lock.unlock();
		}
	}
	
	/**
	 * 通过PageContext中的JspWriter输出，并换行
	 * 
	 * @param pageContext
	 * @param text
	 * @author dongping
	 * @date Jun 13, 2010
	 */
	public static void writeln(PageContext pageContext, String text) {
		JspWriter writer = pageContext.getOut();
	    try {
	        writer.println(text);
	    } catch (IOException e) {
	        logger.error(e.getMessage(), e);
	    }
	}
	
	/**
	 * 使用HttpServletResponse中的PrintWriter输出
	 * @param response
	 * @param text
	 * @author dongping
	 */
	public static void print(HttpServletResponse response, String text){
		try {
			print(response.getWriter(), text);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	/**
	 * PrintWriter输出
	 * @param out
	 * @param text
	 * @author dongping
	 * @date Jun 13, 2010
	 */
	public static void print(PrintWriter out, String text){
		if(out == null){
			logger.error("PrintWriter object does not exist.");
			return;
		}
		
		if(text == null){
			logger.error("To output the object does not exist.");
			return;
		}
		
		try{
			out.print(text);
		}finally{
			out.flush();
			out.close();
		}
	}

	public static Tag getParent(Tag self, Class<?> clazz) throws JspException {
		Tag tag = self.getParent();
		while (!(clazz.isAssignableFrom(tag.getClass()))) {
			tag = tag.getParent();
			if (tag == null) {
				final String message = String.format("Parent tag of class %s of the tag's class %s was not found.", clazz.getClass().getName(), self.getClass().getName());
				logger.error(message);
				throw new JspException(message);
			}
		}
		return tag;
	}

}
