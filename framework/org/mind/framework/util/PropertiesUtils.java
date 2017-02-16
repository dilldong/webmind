package org.mind.framework.util;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.Logger;

/**
 * 读取*.properies文件。
 * 
 * @author dongping
 */
public abstract class PropertiesUtils {
	
	static Logger logger = Logger.getLogger(PropertiesUtils.class);
	
	private static final String DEFAULT_PROPERTIES = "/frame.properties";
	
	/**
	 * 默认参数为frame.properties
	 * @return Properties
	 *
	 * @author dongping
	 * @throws IOException 
	 */
	public static Properties getProperties(){
		return getProperties(PropertiesUtils.class.getResourceAsStream(DEFAULT_PROPERTIES));
	}
	
	/**
	 * 需要指定properties属性文件的绝对路径
	 * @param resPath properties绝对文件路径
	 * @return Properties
	 *
	 * @author dongping
	 * @throws IOException 
	 */
	public static Properties getProperties(String resPath) {
		try {
			return getProperties(new BufferedInputStream(new FileInputStream(resPath)));
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}
	
	/**
	 * 需要指定properties输入流
	 * @param resourceFilePath 可以是BufferedInputStream或InputStream
	 * @return Properties
	 *
	 * @author dongping
	 * @throws IOException 
	 */
	public static Properties getProperties(InputStream in){
		Properties props = new Properties();
		try{
			try{
				props.load(in);
				return props;
			}finally{
				in.close();
			}
		}catch(IOException e){
			logger.error(e.getMessage(), e);
		}
		return null;
	}
	
	
	public static long getLong(Properties property, String key){
		String str = getString(property, key);
		if(str == null || str.trim().isEmpty())
			throw new IllegalArgumentException("Corresponding to the key value is empty or characters, For input string: \""+ str +"\"");
			
		return Long.parseLong(str);
	}
	
	public static int getInteger(Properties property, String key){
		String str = getString(property, key);
		if(str == null || str.trim().isEmpty())
			throw new IllegalArgumentException("Corresponding to the key value is empty or characters, For input string: \""+ str +"\"");
			
		return Integer.parseInt(str);
	}
	
	public static String getString(Properties property, String key){
		if(property == null){
			logger.error("Get Properties object is null, Please call the method: getProperties()");
			return null;
		}
		
		String str = property.getProperty(key);
		if(logger.isDebugEnabled())
			logger.debug(key+ " = " + str);
		return str;
	}

}
