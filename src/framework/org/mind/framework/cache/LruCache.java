/*
 * java.lang.Runtime类中的freeMemory(),totalMemory(),maxMemory()这几个方法的一些问题，
 * 很多人感到很疑惑，为什么，在java程序刚刚启动起来的时候freeMemory()这个方法返回的只有一两兆字节，
 * 而随着java程序往前运行，创建了不少的对象，freeMemory()这个方法的返回有时候不但没有减少，
 * 反而会增加。这些人对freeMemory()这个方法的意义应该有一些误解，他们认为这个方法返回的是操作系统的剩余可用内存，
 * 其实根本就不是这样的。这三个方法反映的都是java这个进程的内存情况，跟操作系统的内存根本没有关系。
 * 下面结合totalMemory(),maxMemory()一起来解释。
 *
 * maxMemory()这个方法返回的是java虚拟机（这个进程）能构从操作系统那里挖到的最大的内存，
 * 以字节为单位，如果在运行java程序的时候，没有添加-Xmx参数，那么就是64兆，
 * 也就是说maxMemory()返回的大约是64*1024*1024字节，这是java虚拟机默认情况下能从操作系统
 * 那里挖到的最大的内存。如果添加了-Xmx参数，将以这个参数后面的值为准，例如:
 * java -cp ClassPath -Xmx512m ClassName，那么最大内存就是512*1024*0124字节。
 *
 * totalMemory()这个方法返回的是java虚拟机现在已经从操作系统那里挖过来的内存大小，
 * 也就是java虚拟机这个进程当时所占用的所有内存。如果在运行java的时候没有添加-Xms参数，
 * 那么，在java程序运行的过程的，内存总是慢慢的从操作系统那里挖的，基本上是用多少挖多少，
 * 直挖到maxMemory()为止，所以totalMemory()是慢慢增大的。如果用了-Xms参数，
 * 程序在启动的时候就会无条件的从操作系统中挖 -Xms后面定义的内存数，然后在这些内存用的差不多的时候，再去挖。
 *
 * freeMemory()是什么呢，如果在运行java的时候没有添加-Xms参数，那么，在java程序运行的过程的，
 * 内存总是慢慢的从操作系统那里挖的，基本上是用多少挖多少，但是java虚拟机100％的情况下是会稍微多挖一点的，
 * 这些挖过来而又没有用上的内存，实际上就是 freeMemory()，所以freeMemory()的值一般情况下都是很小的，
 * 但是如果你在运行java程序的时候使用了-Xms，这个时候因为程序在启动的时候就会无条件的从操作系统中挖-Xms
 * 后面定义的内存数，这个时候，挖过来的内存可能大部分没用上，所以这个时候freeMemory()可能会有些大。
 */

package org.mind.framework.cache;

import org.apache.commons.lang3.StringUtils;
import org.mind.framework.service.Cloneable;
import org.mind.framework.util.DateFormatUtils;
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

        if (interval > 0 && (DateFormatUtils.getMillis() - element.getFirstTime()) > interval) {
            this.removeCache(key);
            log.warn("Remove Cache key, The access time interval expires. key = {}", key);
            return null;
        }

        while (true) {
            if (write.tryLock()) {
                try {
                    element.recordVisited();// record count of visit
                    element.recordTime(DateFormatUtils.getMillis()); // record time of visit
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
            return Collections.EMPTY_LIST;

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
            log.info("Destroy Cacheable@{}, clear all items.", Integer.toHexString(hashCode()));
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
