package org.mind.framework.cache;

import org.apache.commons.lang3.StringUtils;
import org.mind.framework.service.Cloneable;
import org.mind.framework.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Local cache implementation of LRU(Least Recently Used)
 *
 * @author dp
 * @date Nov 26, 2010
 */
public class LruCache extends AbstractCache implements Cacheable {

    private static final Logger log = LoggerFactory.getLogger(LruCache.class);

    /*
     * The maximum number of active cache entries, the default capacity is 1024
     */
    private volatile int capacity = 1024;

    /*
     * entry timeout
     */
    private volatile long timeout = 0L;

    private final Map<String, CacheElement> itemsMap;

    private transient final ReentrantLock lock = new ReentrantLock();

    public static LruCache initCache() {
        return CacheHolder.CACHE_INSTANCE;
    }

    private static class CacheHolder {
        private static final LruCache CACHE_INSTANCE = new LruCache();
    }

    private LruCache() {
        itemsMap = new LinkedHashMap<String, CacheElement>(capacity, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Entry<String, CacheElement> eldest) {
                boolean tooBig = this.size() > LruCache.this.capacity;
                if (tooBig && log.isDebugEnabled())
                    log.debug("Remove the last entry key: {}", eldest.getKey());
                return tooBig;
            }
        };
    }

    /**
     * 添加一个新条目，如果该条目已经存在，将不做任何操作
     *
     * @param key
     * @param value
     * @author dp
     */
    @Override
    public Cacheable addCache(String key, Object value) {
        return addCache(key, value, false);
    }

    /**
     * 添加一个新条目
     *
     * @param key
     * @param value
     * @param forceUpdate false:若条目存在，不做任何操作。 true: 先移除存在的条目，再重新装入;
     * @author dp
     */
    @Override
    public Cacheable addCache(String key, Object value, boolean forceUpdate) {
        return addCache(key, value, forceUpdate, Cloneable.CloneType.NONE);
    }

    @Override
    public Cacheable addCache(String key, Object value, boolean forceUpdate, Cloneable.CloneType type) {
        return this.addCache(key, new CacheElement(value, key, type), forceUpdate);
    }

    @Override
    public Cacheable addCache(String key, CacheElement element) {
        return this.addCache(key, element, false);
    }

    @Override
    public Cacheable addCache(String key, CacheElement element, boolean forceUpdate) {
        String realKey = super.realKey(key);
        lock.lock();
        try {
            // 若 check=true，直接 put 即可实现“替换并移至 LRU 尾部”
            // 若 check=false，仅在不存时才 put
            if (forceUpdate || !this.itemsMap.containsKey(realKey))
                itemsMap.put(realKey, element);

            return this;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CacheElement getCache(String key) {
        return this.getCache(key, timeout);
    }

    @Override
    public CacheElement getCache(String key, long interval) {
        String realKey = super.realKey(key);
        lock.lock();
        try {
            CacheElement element = this.itemsMap.get(realKey);

            if (Objects.isNull(element))
                return null;

            if (interval > 0L) {
                long gap = DateUtils.CachedTime.currentMillis() - element.getFirstTime();
                if (gap > interval) {
                    itemsMap.remove(realKey);
                    return null;
                }
            }

            element.recordVisited();// record count of visit
            element.recordTime(DateUtils.CachedTime.currentMillis()); // record time of visit
            return element;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CacheElement removeCache(String key) {
        lock.lock();
        try {
            return itemsMap.remove(super.realKey(key));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<CacheElement> removeCacheContains(String searchStr) {
        return removeCacheContains(searchStr, null);
    }

    @Override
    public List<CacheElement> removeCacheContains(String searchStr, String[] excludes) {
        return removeCacheContains(searchStr, excludes, Cacheable.CompareType.EQ_FULL);
    }

    @Override
    public List<CacheElement> removeCacheContains(String searchStr, String[] excludes, CompareType excludesRule) {
        lock.lock();
        try {
            if (this.isEmpty())
                return Collections.emptyList();

            List<CacheElement> removeList = new ArrayList<>();
            Iterator<Entry<String, CacheElement>> iterator = itemsMap.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, CacheElement> entry = iterator.next();

                if (!StringUtils.containsIgnoreCase(entry.getKey(), searchStr))
                    continue;

                if (excludes != null && excludes.length > 0 && isExcluded(entry.getKey(), excludes, excludesRule))
                    continue;

                iterator.remove();
                removeList.add(entry.getValue());
            }

            return removeList;
        } finally {
            lock.unlock();
        }
    }


    @Override
    public void destroy() {
        super.destroy();

        lock.lock();
        try {
            if (!this.isEmpty()) {
                itemsMap.clear();
                log.info("Destroy LruCache, clear all items.");
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        return this.itemsMap.isEmpty();
    }

    @Override
    public List<CacheElement> getValues() {
        lock.lock();
        try {
            return this.isEmpty() ? Collections.emptyList() : new ArrayList<>(itemsMap.values());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean containsKey(String key) {
        lock.lock();
        try {
            if (this.isEmpty())
                return false;

            return this.itemsMap.containsKey(super.realKey(key));
        } finally {
            lock.unlock();
        }
    }

    /**
     * 仅对后续新增生效
     */
    @Override
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @Override
    public int getCapacity() {
        return this.capacity;
    }

    @Override
    public long getTimeout() {
        return timeout;
    }
}
