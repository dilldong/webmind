package org.mind.framework.cache;

import org.mind.framework.container.Destroyable;
import org.mind.framework.service.Cloneable;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cache Capability Interface
 *
 * @author dp
 * @date Nov 27, 2010
 */
public interface Cacheable extends Serializable, Destroyable {

    enum CompareType {
        EQ_FULL, EQ_PART
    }

    /**
     * 指定新的LinkedHashMap<String, Object>
     *
     * @param newMap
     * @return
     */
    Cacheable newLinkedMap(LinkedHashMap<String, CacheElement> newMap);

    /**
     * 添加一个新条目，如果该条目已经存在，将不做任何操作
     *
     * @param key
     * @param value
     * @return
     */
    Cacheable addCache(String key, Object value);

    /**
     * 添加一个新条目
     *
     * @param key
     * @param value
     * @param check <br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;false: default, 若条目存在，不做任何操作
     *              <br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;true: 先移除存在的条目，再重新装入
     * @return
     */
    Cacheable addCache(String key, Object value, boolean check);

    Cacheable addCache(String key, Object value, boolean check, Cloneable.CloneType type);

    Cacheable addCache(String key, CacheElement element);

    Cacheable addCache(String key, CacheElement element, boolean check);

    /**
     * 删除缓存
     *
     * @param key
     */
    CacheElement removeCache(String key);


    /**
     * 删除包含的searchStr缓存
     *
     * @param searchStr
     * @return
     * @date July 8, 2021
     */
    List<CacheElement> removeCacheContains(String searchStr);

    /**
     * 删除包含的searchStr缓存, 排除excludes的key值
     *
     * @param searchStr
     * @param excludes  排除的key
     */
    List<CacheElement> removeCacheContains(String searchStr, String[] excludes);

    /**
     * 删除包含的searchStr缓存, 排除excludes的key值
     *
     * @param searchStr
     * @param excludes     排除的key
     * @param excludesRule 排除对象的匹配规则, CompareType.EQ_FULL 精确匹配, CompareType.EQ_PART 包含匹配
     */
    List<CacheElement> removeCacheContains(String searchStr, String[] excludes, Cacheable.CompareType excludesRule);

    /**
     * 获得缓存对象
     *
     * @param key
     * @return
     */
    CacheElement getCache(String key);


    /**
     * 获得缓存对象
     *
     * @param key      缓存对象键值
     * @param interval 毫秒,根据给定的超时值去获取缓存对象，
     *                 如果缓存中太长时间（就是超过给定的interval时间）无访问记录的话，就会重缓存对象池移除掉。
     * @return
     */
    CacheElement getCache(String key, long interval);


    /**
     * 当前缓存对象是否为空
     *
     * @return
     */
    boolean isEmpty();

    /**
     * 判断是否已经存在的key
     *
     * @param key
     * @return
     */
    boolean containsKey(String key);

    /**
     * 获得所有缓存对象的名称
     *
     * @return
     * @date July 8, 2021
     */
    Set<Map.Entry<String, CacheElement>> getEntries();

    /**
     * 指定有效的缓存容量
     *
     * @param capacity
     */
    void setCapacity(int capacity);

    /**
     * 指定默认超时时间
     * @param timeout
     */
    void setTimeout(long timeout);

    int getCapacity();

    long getTimeOut();
}