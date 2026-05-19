package org.mind.framework.annotation.processor;

/**
 * @author: Marcus
 * @date: 2026/5/17
 * @version: 1.0
 */
public interface CacheEventHandler {
    String BEAN_NAME = "cacheEventHandler";
    void onRemoved(String key);
    void onCreated(String key);
    void onUpdated(String key);
    void onExpired(String key);
}
