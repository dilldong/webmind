package org.mind.framework.cache;

import org.redisson.api.RMapCache;

import java.util.concurrent.TimeUnit;

/**
 * @author: Marcus
 * @date: 2026/5/18
 * @version: 1.0
 */
public interface CacheEventPublisher {
    String KEY_EVENT_MAPCACHE = "webmind:sync:cache:listener";
    String BEAN_NAME = "cacheEventPublisher";

    CacheEventPublisher NO_PUBLISHER = new CacheEventPublisher() {
        @Override
        public void registerCacheable(String key, Cacheable cacheable) {
            // do nothing
        }

        @Override
        public void publish(String key, long expire, TimeUnit unit) {
            // do nothing
        }

        @Override
        public RMapCache<String, String> getCacheEventListener() {
            return null;
        }
    };

    /**
     * 注册 key 前缀与其对应的 Cacheable，供事件回调时定向清除本地缓存。
     */
    void registerCacheable(String key, Cacheable cacheable);

    /**
     * 发布缓存事件
     */
    void publish(String key, long expire, TimeUnit unit);

    /**
     * 获取当前的 (RMapCache) 同步器对象
     */
    RMapCache<String, String> getCacheEventListener();
}