package org.mind.framework.cache;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.util.DateUtils;
import org.mind.framework.util.MatcherUtils;
import org.redisson.api.RMapCache;
import org.redisson.api.map.event.EntryCreatedListener;
import org.redisson.api.map.event.EntryExpiredListener;
import org.redisson.api.map.event.EntryRemovedListener;
import org.redisson.api.map.event.EntryUpdatedListener;

import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Marcus
 * @version 1.0
 * @date 2026/5/21
 */
@Slf4j
public class DefaultCacheEventPublisher implements CacheEventPublisher {

    /**
     * Cacheable → staticKey 列表。
     * 同一 Cacheable 对象引用可能对应多个不同的 staticKey
     */
    private final Map<Cacheable, Set<String>> cacheableRegistry;

    private final String cacheSyncName;

    private final CacheEventHandler cacheEventHandler;

    private final RMapCache<String, String> cacheEventListener;

    public DefaultCacheEventPublisher(CacheEventHandler cacheEventHandler, String cacheSyncName) {
        this.cacheEventHandler = cacheEventHandler;
        this.cacheSyncName = cacheSyncName;

        // IdentityHashMap 用 == 而非 equals 判重, 不依赖 equals/hashCode 的实现
        this.cacheableRegistry = Collections.synchronizedMap(new IdentityHashMap<>());
        this.cacheEventListener = this.registerCacheSyncListener();
    }

    @Override
    public void publish(String key, long expire, TimeUnit unit) {
        if (expire == 0L) {
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
    public void registerCacheable(String key, Cacheable cacheable) {
        Set<String> result = cacheableRegistry.computeIfAbsent(cacheable, keys -> ConcurrentHashMap.newKeySet());

        // 拆解 key 前缀
        if (MatcherUtils.checkCount(key, MatcherUtils.PARAM_MATCH_PATTERN) > 0) {
            String prefix = StringUtils.substringBefore(key, "#{");
            if (StringUtils.isNotEmpty(prefix))
                result.add(prefix);
            else
                log.warn("Cannot parse prefix from key: {}, cache eviction may be inaccurate", key);
            return;
        }

        result.add(key);
    }

    private RMapCache<String, String> registerCacheSyncListener() {
        RMapCache<String, String> eventListener = RedissonHelper.getClient().getMapCache(cacheSyncName);

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
     * 根据完整 key 匹配注册表中的前缀，定向清除对应 Cacheable 的本地缓存。
     * 匹配规则：完整 key 以注册的前缀开头（兼容静态key和带动态参数的key）。
     */
    private void evictLocalCache(String key) {
        cacheableRegistry.forEach((cacheable, staticKeys) -> {
            boolean matched = staticKeys.stream().anyMatch(key::startsWith);

            if (matched) {
                cacheable.removeCache(key);
                log.debug("Evicted local cache key: {}", key);
            }
        });
    }
}