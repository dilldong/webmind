package org.mind.framework.cache;

import java.io.Serializable;

/**
 * 缓存能力接口
 * 
 * @author dongping
 * @date Nov 27, 2010
 */
public interface Cacheable extends Serializable {

	/**
	 * 添加一个新条目，如果该条目已经存在，将不做任何操作
	 * @param key
	 * @param value
	 * @return
	 * @author dongping
	 * @date Nov 27, 2010
	 */
	Cacheable addCache(String key, Object value);
	
	/**
	 * 添加一个新条目
	 * @param key
	 * @param value
	 * @param check <br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;false: default, 若条目存在，不做任何操作
	 * 				<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;true: 先移除存在的条目，再重新装入
	 * @return
	 * @author dongping
	 * @date Nov 27, 2010
	 */
	Cacheable addCache(String key, Object value, boolean check);

	/**
	 * 删除缓存
	 * @param key
	 * @author dongping
	 * @date Nov 27, 2010
	 */
	void removeCache(String key);

	/**
	 * 获得缓存对象
	 * @param key
	 * @return
	 * @author dongping
	 * @date Nov 27, 2010
	 */
	CacheElement getCache(String key);
	
	
	/**
	 * 获得缓存对象
	 * @param key 缓存对象键值
	 * @param interval 毫秒,根据给定的超时值去获取缓存对象，
	 * 如果缓存中太长时间（就是超过给定的interval时间）无访问记录的话，就会重缓存对象池移除掉。
	 * 
	 * @return
	 * @author dongping
	 * @date Nov 27, 2010
	 */
	CacheElement getCache(String key, long interval);

	
	/**
	 * 当前缓存对象是否为空
	 * @return
	 * @author dongping
	 * @date Sep 17, 2011
	 */
	boolean isEmpty();
	
	/**
	 * 判断是否已经存在的key
	 * @param key
	 * @return
	 * @author dongping
	 * @date Nov 18, 2011
	 */
	public boolean containsKey(String key);
	
	/**
	 * 获得所有的缓存对象
	 * @return
	 * @author dongping
	 * @date Nov 27, 2010
	 */
	CacheElement[] getCaches();

	/**
	 * 获得所有缓存对象的名称
	 * @return
	 * @author dongping
	 * @date Nov 27, 2010
	 */
	String[] getKeys();
	
}