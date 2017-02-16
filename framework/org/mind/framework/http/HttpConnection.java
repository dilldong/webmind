package org.mind.framework.http;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

public class HttpConnection {

	private static final Logger logger = Logger.getLogger(HttpConnection.class);
	
	public static final String CONTENT_TYPE_TEXT = "text/html";
	public static final String CONTENT_TYPE_WWW = "application/x-www-form-urlencoded";
	public static final String CONTNT_TYPE_IMAGE = "image/jpeg";
	
	private transient final Lock lock = new ReentrantLock();
	
	private LiteCookieManager cookieManager;
    
    public HttpConnection(){
    	this.cookieManager = new LiteCookieManager();
    }
    
    private HttpResponse request(
    		String addr, 
    		String host, 
    		String contentType, 
    		String referer, 
    		String cookie, 
    		String params,
    		boolean getCookie){
    	
    	try{
    		URL url = new URL(addr);
    		HttpURLConnection con = (HttpURLConnection)url.openConnection();
    		con.setRequestProperty("User-Agent", "Mozilla/5.0(Windows NT 6.1; WOW64; rv:27.0) Gecko/20100101 Firefox/27.0");
    		con.setRequestProperty("Accept", "text/html");
    		con.setRequestProperty("Accept-Language", "zh-cn");
    		con.setRequestProperty("Cache-Control", "max-age=0");
    		con.setRequestProperty("Connection", "keep-alive");
    		con.setRequestProperty("Host", host);
    		
    		if(contentType != null)
    			con.setRequestProperty("Content-Type", contentType);
    		
    		con.setRequestProperty("Referer", referer == null? addr : referer);
    		con.setConnectTimeout(30 * 1000);
    		con.setDoInput(true);
    		con.setUseCaches(false);
    		con.setRequestMethod("GET");
    		
    		if(!getCookie && (cookie != null || this.cookieManager.containsCookie(con)))
    			this.cookieManager.setCookies(con);
    		
//    		if(cookie != null)
//    			con.setRequestProperty("Cookie", cookie);
    		
    		int paramLength = params == null? 0 : params.length();
    		
    		if(paramLength > 0){
    			con.setRequestProperty("Content-Length", String.valueOf(paramLength));
    			con.setDoOutput(true);
    			con.setRequestMethod("POST");
    		}
    		con.connect();
    		
    		if(paramLength > 0){
    			OutputStream out = con.getOutputStream();
    			out.write(params.getBytes());
    			out.flush();
    			out.close();
    		}
    		
    		// 若已经获取一次后，不第二次获取
    		if(getCookie){
	    		String setCookie = con.getHeaderField("Set-Cookie");
	    		
	    		if(setCookie != null){
	    			this.cookieManager.storeCookies(con);
	    			con.disconnect();
	    			return this.request(addr, host, contentType, referer, setCookie, params, false);
	    		}
    		}
    		
    		return new HttpResponse(con);
    		
    	}catch(Exception e){
    		logger.error(e.getMessage(), e);
    	}
    	
    	return null;
    	
    }
    
    public HttpResponse get(String addr){
    	return this.get(addr, CONTENT_TYPE_WWW);
    }
    
    public HttpResponse get(String addr, String contentType){
    	return this.get(addr, contentType, false);
    }
    
    public HttpResponse get(String addr, String contentType, boolean setCookie){
    	return this.post(addr, contentType, setCookie);
    }
    
    public HttpResponse post(String addr, String ... params){
    	return this.post(addr, CONTENT_TYPE_WWW, false, params);
    }
    
    public HttpResponse post(String addr, boolean getCookie, String ... params){
    	return this.post(addr, CONTENT_TYPE_WWW, getCookie, params);
    }

    public HttpResponse post(String addr, String contentType, boolean setCookie, String ... params){
		lock.lock();
		try{
			StringBuffer buf = new StringBuffer();
			String bufParams = null;
			
			if(params != null && params.length > 0){
				for(String str : params)
					buf.append(str).append("&");
				
				bufParams = buf.substring(0, buf.length()-1);
			}
			
			int lastref = addr.lastIndexOf(".") + 4;
			if(lastref > addr.length())
				lastref --;
			
			String referer = addr.substring(0, lastref);
			
			String lastfix = addr.substring(addr.indexOf("//") + 2);
			String host = lastfix.substring(0, lastfix.indexOf("/"));
			
			return this.request(addr, host, contentType, referer, null, bufParams, setCookie);
			
		}finally{
			lock.unlock();
		}
	}
}
