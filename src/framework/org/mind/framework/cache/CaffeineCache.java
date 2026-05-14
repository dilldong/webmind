package org.mind.framework.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.service.Cloneable;
import org.mind.framework.util.DateUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Implemented based on Caffeine local cache
 *
 * @author: Marcus
 * @date: 2026/5/12
 * @version: 1.0
 */
@Slf4j
public class CaffeineCache extends AbstractCache implements Cacheable {
    // Caffeine 内部 TimerWheel 的安全上限约为 Long.MAX_VALUE >>> 1
    private static final long NO_EXPIRY_NANOS = Long.MAX_VALUE >>> 1;

    // expire 和 capacity 在构建时固定，不支持运行时动态修改
    @Getter
    private final int capacity;

    private final long timeout; // 毫秒，0: 不过期

    private final Cache<String, CacheElement> store;

    public CaffeineCache(int capacity) {
        this(capacity, 0L);
    }

    public CaffeineCache(int capacity, long globalTtlMillis) {
        this.capacity = capacity;
        this.timeout = globalTtlMillis;

        this.store = Caffeine.newBuilder()
                .maximumSize(capacity)
                .recordStats()
                .expireAfter(new Expiry<String, CacheElement>() {
                    @Override
                    public long expireAfterCreate(String key, CacheElement value, long currentTime) {
                        return value.getTtlMillis() > 0L
                                ? TimeUnit.MILLISECONDS.toNanos(value.getTtlMillis())
                                : (timeout > 0L ? TimeUnit.MILLISECONDS.toNanos(timeout) : NO_EXPIRY_NANOS);
                    }

                    @Override
                    public long expireAfterUpdate(String key, CacheElement value, long currentTime, long currentDuration) {
                        return expireAfterCreate(key, value, currentTime);
                    }

                    @Override
                    public long expireAfterRead(String key, CacheElement value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    public static CaffeineCache of(int capacity) {
        return new CaffeineCache(capacity);
    }

    public static CaffeineCache of(int capacity, long globalTtlMillis) {
        return new CaffeineCache(capacity, globalTtlMillis);
    }

    @Override
    public Cacheable addCache(String key, Object value) {
        return addCache(key, value, false);
    }

    @Override
    public Cacheable addCache(String key, Object value, boolean forceUpdate) {
        return addCache(key, value, forceUpdate, Cloneable.CloneType.NONE);
    }

    @Override
    public Cacheable addCache(String key, Object value, boolean forceUpdate, Cloneable.CloneType type) {
        return addCache(key, new CacheElement(value, key, type), forceUpdate);
    }

    @Override
    public Cacheable addCache(String key, CacheElement element) {
        return addCache(key, element, false);
    }

    @Override
    public Cacheable addCache(String key, CacheElement element, boolean forceUpdate) {
        String realKey = super.realKey(key);
        if (forceUpdate)
            store.put(realKey, element);
        else
            store.asMap().putIfAbsent(realKey, element);

        return this;
    }

    @Override
    public CacheElement getCache(String key) {
        String realKey = super.realKey(key);
        CacheElement element = this.store.getIfPresent(realKey);

        if (Objects.isNull(element))
            return null;

        element.recordVisited();// record count of visit
        element.recordTime(DateUtils.CachedTime.currentMillis()); // record time of visit
        return element;
    }

    @Override
    public CacheElement getCache(String key, long interval) {
        CacheElement element = getCache(key);
        if (Objects.isNull(element))
            return null;

        // interval > 0 时做软过期判断
        if (interval > 0L) {
            long gap = DateUtils.CachedTime.currentMillis() - element.getFirstTime();
            if (gap > interval) {
                store.invalidate(super.realKey(key));
                return null;
            }
        }
        return element;
    }

    // 用 asMap().remove() 保证原子性（底层是 ConcurrentHashMap.remove）
    @Override
    public CacheElement removeCache(String key) {
        return store.asMap().remove(super.realKey(key));
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
        if (isEmpty())
            return Collections.emptyList();

        boolean nonNullExcludes = ArrayUtils.isNotEmpty(excludes);
        List<CacheElement> removed = new CopyOnWriteArrayList<>();

        store.asMap().entrySet().removeIf(entry -> {
            if (!StringUtils.containsIgnoreCase(entry.getKey(), searchStr))
                return false;

            if (nonNullExcludes && isExcluded(entry.getKey(), excludes, excludesRule))
                return false;

            removed.add(entry.getValue());
            return true;
        });

        return removed;
    }

    @Override
    public boolean isEmpty() {
        return store.estimatedSize() == 0L;
    }

    @Override
    public boolean containsKey(String key) {
        return store.getIfPresent(super.realKey(key)) != null;
    }

    @Override
    public List<CacheElement> getValues() {
        // 触发 pending 的驱逐，让 estimatedSize 更可信
        store.cleanUp();

        return isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(store.asMap().values());
    }

    @Override
    public void setCapacity(int capacity) {
        throw new UnsupportedOperationException("Not supported runtime modification, Please use CaffeineCache.of() to specify capacity");
    }

    @Override
    public void setTimeout(long timeout) {
        throw new UnsupportedOperationException("Not supported runtime modification, Please use CaffeineCache.of() to specify timeout");
    }

    /**
     * 暴露 Caffeine 统计
     */
    public CacheStats stats() {
        return store.stats();
    }

    /**
     * 暴露原生 Caffeine Cache，供需要精细控制的场景直接使用
     */
    public Cache<String, CacheElement> nativeCache() {
        return store;
    }

    @Override
    public long getTimeout() {
        return timeout;
    }

    @Override
    public void destroy() {
        super.destroy();
        store.invalidateAll();
        log.info("Destroy CaffeineCache, clear all items.");
    }
}
