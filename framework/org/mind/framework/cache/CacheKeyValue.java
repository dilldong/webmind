package org.mind.framework.cache;


/**
 * 为了迎合使用Cache，对系统需要缓存对象的key进行定义。
 * 
 * 结构图如下：
 * |-项目名称
 *    |-非共享信息
 *    	   |-用户资料
 *    	   |-用户权限
 *    	   |-其它
 *    |-共享信息
 *         |-全文搜索
 *         |-
 *         |-其它
 * 
 * @author dongping
 */
public enum CacheKeyValue {
	
	/**
	 * 分割缓存key值的分隔符："."
	 */
	SEPARATOR("."),

	/**
	 * 系统缓存根名称
	 */
	ROOT_KEY("org.mind.framework.cache_key"),
	
	/**
	 * 非共享信息子节点(ROOT_KEY.NON_SHARED_INFO)
	 * <br/>如：org.mind.framework.cache_key.userNonShared
	 */
	USER_NON_SHARED_KEY(ROOT_KEY.name + SEPARATOR.name + "userNonShared"),
	
	/**
	 * 共享信息子节点(ROOT_KEY.userShared)
	 * <br/>如：org.mind.framework.cache_key.userShared
	 */
	USER_SHARED_KEY(ROOT_KEY.name + SEPARATOR.name + "userShared");
	
	private String name;
	
	public String getName() {
		return name;
	}
	
	private CacheKeyValue(String name){
		this.name = name;
	}
	
	@Override
	public String toString() {
		return this.getName();
	}
}
