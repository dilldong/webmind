package org.mind.framework.dispatcher.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mind.framework.util.PropertiesUtils;

import com.oreilly.servlet.multipart.FilePart;
import com.oreilly.servlet.multipart.MultipartParser;
import com.oreilly.servlet.multipart.ParamPart;
import com.oreilly.servlet.multipart.Part;

/**
 * Wrapper class for multipart HTTP request which usually used to upload file.
 * 
 * @author dongping
 */
public class MultipartHttpServletRequest extends HttpServletRequestWrapper{
	
	private static final Log log = LogFactory.getLog(MultipartHttpServletRequest.class);
	
	private static final int SIZE = 10 * 1024 * 1024;
	
	private static final int MAX_SIZE = 16 * 1024 * 1024;
	
	private static final int MIN_SIZE = 0;
	
	private static final String DEFAULT_CHARENCODEING = "UTF-8";
	
	private int defaultSize;

	private Properties property;
	
	// multipart fileds
	private Map<String, List<String>> formParams;
	
	// multipart files
	private List<FileItem> multiPartFiles;
	
	private boolean requestFailed;
	
	private int requestContentLength;
	
	/**
	 * Check the content type to make sure it's "multipart/form-data"
	 * Access header two ways to work around WebSphere oddities
	 * @param request
	 * @return
	 */
	public static boolean isMultipartRequest(HttpServletRequest request){
		String type = null;
	    String type1 = request.getHeader("Content-Type");
	    String type2 = request.getContentType();
	    
	    // If one value is null, choose the other value
	    if (type1 == null && type2 != null)
	    	type = type2;
	    
	    else if (type2 == null && type1 != null)
	    	type = type1;
	    
	    // If neither value is null, choose the longer value
	    else if (type1 != null && type2 != null)
	    	type = (type1.length() > type2.length() ? type1 : type2);

	    if (type == null)
	    	return false;
	    
	    if(type.toLowerCase().startsWith("multipart/form-data"))
	    	return true;
	    
	    return false;
	}
	
	public boolean isRequestFailed(){
		return this.requestFailed;
	}
	
	public int getDefaultSize(){
		return this.defaultSize;
	}
	
	public int getRequestContentLength(){
		return this.requestContentLength;
	}
	
	public MultipartHttpServletRequest(HttpServletRequest request) throws IOException{
		super(request);
		this.property = PropertiesUtils.getProperties();
		
		defaultSize = PropertiesUtils.getInteger(property, "upload.size");
		defaultSize = (defaultSize <= MIN_SIZE || defaultSize > MAX_SIZE) ? SIZE : defaultSize;
		
		// Check the content length to prevent denial of service attacks
		this.requestContentLength = request.getContentLength();
	    if (this.requestContentLength > defaultSize) {
	    	log.warn("Posted content length of " + this.requestContentLength + " exceeds limit of " + defaultSize);
	    	this.requestFailed = true;
	    	return;
	    }
	    
		// Parse the incoming multipart.
		MultipartParser multiParser = 
				new MultipartParser(request, defaultSize, true, true, DEFAULT_CHARENCODEING);
		
		if (request.getQueryString() != null) {
			this.queryString();
		}
		
		// processor multipart request
		this.parserRequest(multiParser, request);
		
	}
	
	private void parserRequest(MultipartParser multiParser, HttpServletRequest request) throws IOException{
		this.formParams = new HashMap<String, List<String>>();
		this.multiPartFiles = new ArrayList<FileItem>(6);
		Part part = null;
		
		while((part = multiParser.readNextPart()) != null){
			// request form fileds
			if(part.isParam()){
				ParamPart paramPart = (ParamPart) part;
		        this.addFiled(part.getName(), paramPart.getStringValue());
		        
			}else if(part.isFile()){// request upload files
				final FilePart filePart = (FilePart)part;
				
				FileItem item = new FileItem();
				item.setContentType(filePart.getContentType());
				item.setFiledName(filePart.getName());
				item.setFileName(filePart.getFileName());
				
			    long size = 0;
			    int read = -1;
			    byte[] buf = new byte[8 * 1024];
			    
			    InputStream in = filePart.getInputStream();
			    ByteArrayOutputStream byteStream = null;
			    try {
			    	byteStream = new ByteArrayOutputStream();
			    	try{
						while((read = in.read(buf)) != -1) {
							byteStream.write(buf, 0, read);
							size += read;
						}
						
			    	}finally{
			    		/*
			    		 * When a close method is called on a such a class, 
			    		 * it automatically performs a flush. 
			    		 * There is no need to explicitly call flush before calling close.
			    		 * 
			    		 * byteStream.flush();
			    		 */
			    		byteStream.close();
			    	}
			    	
			    	item.setStream(byteStream.toByteArray());
			    	item.setSize(size);
				    multiPartFiles.add(item);
				    
				} catch (IOException e) {
					log.error(e.getMessage(), e);
				}
			}
		}
	}
	
	private void addFiled(String name, String value){
		List<String> list = this.formParams.get(name);
		if(list == null){
        	list = new ArrayList<String>(5);
        	this.formParams.put(name, list);
        }
        	
		list.add(value);
	}
	
	private void queryString(){
		
	}
	
	/**
	 * get multipart files objects by http request.
	 * @return
	 */
	public List<FileItem> getMultipartFiles(){
		return this.multiPartFiles;
	}
	
	public String getParameter(String name){
		if(this.formParams == null)
			return null;
		
		List<String> list = this.formParams.get(name);
		if(list == null || list.isEmpty())
			return null;
		
		return list.get(0);
	}

	
	@Override
	public Map<String, String[]> getParameterMap() {
		if(this.formParams == null)
			return null;
		
		Map<String, String[]> map = 
				new HashMap<String, String[]>(this.formParams.size());
		
		Set<Entry<String, List<String>>> entries = this.formParams.entrySet();
		for(Entry<String, List<String>> en : entries){
			List<String> list = en.getValue();
			map.put(en.getKey(), list.toArray(new String[list.size()]));
		}
		return map;
	}

	@Override
	public Enumeration<String> getParameterNames() {
		if(this.formParams == null)
			return null;
		
		return Collections.enumeration(this.formParams.keySet());
	}

	@Override
	public String[] getParameterValues(String name) {
		if(this.formParams == null)
			return null;
		
		List<String> list = this.formParams.get(name);
		return list.toArray(new String[list.size()]);
	}
	
	
	public class FileItem{
		private String filedName;
		private String fileName;
		private String contentType;
		private byte[] stream;
		private long size;
		public String getFiledName() {
			return filedName;
		}
		public void setFiledName(String filedName) {
			this.filedName = filedName;
		}
		public String getFileName() {
			return fileName;
		}
		public void setFileName(String fileName) {
			this.fileName = fileName;
		}
		public String getContentType() {
			return contentType;
		}
		public void setContentType(String contentType) {
			this.contentType = contentType;
		}
		public byte[] getStream() {
			return stream;
		}
		public void setStream(byte[] stream) {
			this.stream = stream;
		}
		public long getSize() {
			return size;
		}
		public void setSize(long size) {
			this.size = size;
		}
	}

	
}
