package org.mind.framework.annotation.processor;

import org.mind.framework.cache.Cacheable;

import java.util.concurrent.TimeUnit;

/**
 * @author: Marcus
 * @date: 2026/5/18
 * @version: 1.0
 */
public interface CacheEventPublisher {
    String KEY_EVENT_MAPCACHE = "webmind:sync:cache:listener";

    CacheEventPublisher NO_PUBLISHER = new CacheEventPublisher() {
        @Override
        public void registerCacheable(String key, Cacheable cacheable) {
            // do nothing
        }

        @Override
        public void publish(String key, long expire, TimeUnit unit) {
            // do nothing
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
}
