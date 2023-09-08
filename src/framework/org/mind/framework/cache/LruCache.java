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
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Cache implementation of LRU(Least Recently Used)
 *
 * @author dp
 * @date Nov 26, 2010
 */
public class LruCache extends AbstractCache implements Cacheable {

    private static final Logger log = LoggerFactory.getLogger(LruCache.class);

    /*
     * The maximum number of active cache entries, the default capacity is 1024
     */
    private int capacity = 1024;

    /*
     * entry timeout
     */
    private long timeout = 0L;

    private Map<String, CacheElement> itemsMap;

    private transient final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    private transient final Lock read = readWriteLock.readLock();

    private transient final Lock write = readWriteLock.writeLock();

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

    @Override
    public Cacheable newLinkedMap(LinkedHashMap<String, CacheElement> newMap) {
        this.itemsMap = newMap;
        return this;
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
     * @param check false:若条目存在，不做任何操作。 true:先移除存在的条目，再重新装入;
     * @author dp
     */
    @Override
    public Cacheable addCache(String key, Object value, boolean check) {
        return addCache(key, value, check, Cloneable.CloneType.ORIGINAL);
    }

    @Override
    public Cacheable addCache(String key, Object value, boolean check, Cloneable.CloneType type) {
        return this.addCache(key, new CacheElement(value, key, type), check);
    }

    @Override
    public Cacheable addCache(String key, CacheElement element) {
        return this.addCache(key, element, false);
    }

    @Override
    public Cacheable addCache(String key, CacheElement element, boolean check) {
        if (this.containsKey(key)) {
            if (check)
                return this.replace(key, element);

            if (log.isDebugEnabled())
                log.debug("The Cache key already exists.");
            return this;
        }

        while (true) {
            if (write.tryLock()) {
                try {
                    itemsMap.put(super.realKey(key), element);
                    return this;
                } finally {
                    write.unlock();
                }
            }
        }
    }

    @Override
    public CacheElement getCache(String key) {
        return this.getCache(key, timeout);
    }

    @Override
    public CacheElement getCache(String key, long interval) {
        CacheElement element = this.getElement(key);
        if (Objects.isNull(element))
            return null;

        if (interval > 0 && (DateUtils.getMillis() - element.getFirstTime()) > interval) {
            this.removeCache(key);
            log.warn("Remove Cache key, The access time interval expires. key = {}", key);
            return null;
        }

        while (true) {
            if (write.tryLock()) {
                try {
                    element.recordVisited();// record count of visit
                    element.recordTime(DateUtils.getMillis()); // record time of visit
                    return element;
                } finally {
                    write.unlock();
                }
            }
        }

    }

    @Override
    public CacheElement removeCache(String key) {
        if (this.containsKey(key)) {
            while (true) {
                if (write.tryLock()) {
                    try {
                        return itemsMap.remove(super.realKey(key));
                    } finally {
                        write.unlock();
                    }
                }
            }
        }
        return null;
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
    public List<CacheElement> removeCacheContains(String searchStr, String[] excludes, Cacheable.CompareType excludesRule) {
        if (this.isEmpty())
            return Collections.emptyList();

        List<CacheElement> removeList = new ArrayList<>();
        Iterator<Entry<String, CacheElement>> iterator = this.getEntries().iterator();
        boolean exclude;

        while (iterator.hasNext()) {
            Map.Entry<String, CacheElement> entry = iterator.next();
            if (StringUtils.containsIgnoreCase(entry.getKey(), searchStr)) {
                // Exclude
                if (excludes != null && excludes.length > 0) {
                    // true: continue find, false: for delete
                    exclude = false;
                    for (String exKey : excludes) {
                        exclude = Cacheable.CompareType.EQ_FULL == excludesRule ?
                                StringUtils.equals(entry.getKey(), exKey) :
                                StringUtils.contains(entry.getKey(), exKey);
                        if (exclude)
                            break;
                    }

                    if (exclude)
                        continue;
                }

                iterator.remove();
                removeList.add(entry.getValue());
            }
        }

        return removeList;
    }


    @Override
    public synchronized void destroy() {
        super.destroy();
        if (!this.isEmpty()) {
            itemsMap.clear();
            itemsMap = null;
            log.info("Destroy Cacheable@{}, clear all items.", this.getClass().getSimpleName());
        }
    }

    @Override
    public boolean isEmpty() {
        return Objects.isNull(this.itemsMap) || this.itemsMap.isEmpty();
    }

    @Override
    public Set<Entry<String, CacheElement>> getEntries() {
        if (this.isEmpty())
            return Collections.emptySet();

        return itemsMap.entrySet();
    }

    @Override
    public boolean containsKey(String key) {
        read.lock();
        try {
            if (this.isEmpty())
                return false;

            return this.itemsMap.containsKey(super.realKey(key));
        } finally {
            read.unlock();
        }
    }

    private CacheElement getElement(String key) {
        read.lock();
        try {
            if (this.isEmpty())
                return null;

            String realKey = super.realKey(key);
            if (this.itemsMap.containsKey(realKey))
                return this.itemsMap.get(realKey);

            return null;
        } finally {
            read.unlock();
        }
    }

    private Cacheable replace(String key, CacheElement element) {
        while (true) {
            if (write.tryLock()) {
                try {
                    itemsMap.replace(super.realKey(key), element);
                    return this;
                } finally {
                    write.unlock();
                }
            }
        }
    }


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
    public long getTimeOut() {
        return timeout;
    }
}
