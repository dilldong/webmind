package org.mind.framework.cache;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.util.DateUtils;
import org.redisson.api.RMapCache;
import org.redisson.api.map.event.EntryCreatedListener;
import org.redisson.api.map.event.EntryExpiredListener;
import org.redisson.api.map.event.EntryRemovedListener;
import org.redisson.api.map.event.EntryUpdatedListener;
import org.redisson.client.codec.StringCodec;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Marcus
 * @version 1.0
 * @date 2026/5/21
 */
@Slf4j
public class DefaultCacheEventPublisher implements CacheEventPublisher {

    /**
     * 本地 Cacheable 列表。
     */
    private final Set<Cacheable> cacheableRegistry;

    @Getter
    private final String cacheSyncName;

    @Getter
    private final CacheEventHandler cacheEventHandler;

    @Getter
    private final RMapCache<String, String> cacheEventListener;

    public DefaultCacheEventPublisher(CacheEventHandler cacheEventHandler, String cacheSyncName) {
        this.cacheEventHandler = cacheEventHandler;
        this.cacheSyncName = cacheSyncName;

        this.cacheableRegistry = Collections.synchronizedSet(new HashSet<>());
        this.cacheEventListener = this.registerCacheSyncListener();
    }

    @Override
    public void publish(String key, long expire, TimeUnit unit) {
        if (expire <= 0L) {
            cacheEventListener.fastPutAsync(key, Instant.MAX.toString());
            return;
        }

        long delay = Math.max(1L, unit.toMillis(expire) - 20L);
        cacheEventListener.fastPutAsync(
                key,
                Instant.ofEpochMilli(DateUtils.CachedTime.currentMillis() + delay).toString(),
                delay,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void registerCacheable(Cacheable cacheable) {
        cacheableRegistry.add(cacheable);
    }

    protected RMapCache<String, String> buildRMapCache(String cacheSyncName) {
        return RedissonHelper.getClient().getMapCache(cacheSyncName, StringCodec.INSTANCE);
    }

    protected RMapCache<String, String> registerCacheSyncListener() {
        RMapCache<String, String> eventListener = this.buildRMapCache(cacheSyncName);

        eventListener.addListenerAsync((EntryRemovedListener<String, String>) event -> {
            log.debug("Entry removed, key: {}, expire: {}", event.getKey(), event.getValue());
            evictLocalCache(event.getKey());

            if (Objects.nonNull(cacheEventHandler))
                cacheEventHandler.onRemoved(event.getKey());
        });

        eventListener.addListenerAsync((EntryExpiredListener<String, String>) event -> {
            log.debug("Entry expired, key: {}, expire: {}", event.getKey(), event.getValue());
            evictLocalCache(event.getKey());

            if (Objects.nonNull(cacheEventHandler))
                cacheEventHandler.onExpired(event.getKey());
        });

        eventListener.addListenerAsync((EntryUpdatedListener<String, String>) event -> {
            log.debug("Entry updated, key: {}, expire: {}", event.getKey(), event.getValue());
            evictLocalCache(event.getKey());

            if (Objects.nonNull(cacheEventHandler))
                cacheEventHandler.onUpdated(event.getKey());
        });

        eventListener.addListenerAsync((EntryCreatedListener<String, String>) event -> {
            log.debug("Entry created, key: {}, expire: {}", event.getKey(), event.getValue());

            if (Objects.nonNull(cacheEventHandler))
                cacheEventHandler.onCreated(event.getKey());
        });

        return eventListener;
    }

    /**
     * 清除对应 Cacheable 的本地缓存
     */
    protected void evictLocalCache(String key) {
        cacheableRegistry.forEach(cacheable -> {
            cacheable.removeCache(key);
            log.debug("Evicted local-cache key: {}", key);
        });
    }
}