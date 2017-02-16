package org.mind.framework.cache;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class AbstractCache {
	
	private static final Log log = LogFactory.getLog(AbstractCache.class);

//	public abstract int getSize();
	
//	public abstract String[] names();
	
	private String cacheName = "MY-Cache";
	
	protected AbstractCache(){
		
	}
	
	protected AbstractCache(String cacheName){
		this.cacheName = cacheName;
	}
	
	protected void init(){
		
	}
	
	protected void process(){
		
	}
	
	protected String realKey(CacheKeyValue prefix, String key){
		return
			new StringBuffer()
				.append(prefix)
				.append(CacheKeyValue.SEPARATOR)
				.append(key)
				.toString();
	}
	
	/**
	 * 关闭Cache
	 * 
	 * @author dongping
	 * @date Nov 26, 2010
	 */
	protected void destroy() {
		if(log.isInfoEnabled())
			log.info("Destroy "+ cacheName +" manager.");
	}

	public String getCacheName() {
		return cacheName;
	}

	public void setCacheName(String cacheName) {
		this.cacheName = cacheName;
	}
}
