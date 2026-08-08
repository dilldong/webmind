package org.mind.framework.cache;

import lombok.extern.slf4j.Slf4j;
import org.mind.framework.ContextSupport;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.helper.RedissonHelper;
import org.redisson.api.RMapCache;
import org.redisson.api.RType;
import org.redisson.api.options.KeysScanOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * @author Marcus
 * @version 1.0
 * @date 2026/5/23
 */
@Slf4j
public final class CacheUtils {
    private static final Supplier<?> NO_OP = () -> null;

    public static <T> T get(String key, Cacheable cacheable) {
        return get(key, cacheable, NO_OP);
    }

    public static <T> T get(String key, Cacheable cacheable, Supplier<?> action) {
        CacheElement element = cacheable.getCache(key);

        // L1
        if (Objects.nonNull(element)) {
            return inferAndConvertNull(element.getValue());
        }

        // L2
        RType rType = null;
        try {
            rType = RedissonHelper.getClient()
                    .getKeys()
                    .getType(key);
        } catch (Exception ignored) {}

        if (Objects.isNull(rType)) {
            return (T) action.get();
        }

        switch (rType) {
            case MAP:
                return (T) RedissonHelper.getInstance().getMapWithLock(key);
            case SET:
                return (T) RedissonHelper.getInstance().getSetWithLock(key);
            case LIST:
                return (T) RedissonHelper.getInstance().getListWithLock(key);
            case OBJECT:
                return RedissonHelper.getInstance().getWithLock(key);
            default:
                return (T) action.get();
        }
    }

    public static void clear(String key) {
        deleteAsync(key).toCompletableFuture().join();
    }

    public static void clearAsync(String key) {
        deleteAsync(key);
    }

    public static void clearByPatternAsync(String keyPattern) {
        List<String> listKeys = new ArrayList<>();
        RedissonHelper.getClient()
                .getKeys()
                // 实际返回数量由 pattern 匹配率决定，不保证每次恰好返回 200 个 key
                .getKeys(KeysScanOptions.defaults().pattern(keyPattern).chunkSize(200))
                .forEach(listKeys::add);

        if (listKeys.isEmpty()) {
            RMapCache<String, String> rMapCache = getEventSyncCache();
            // 直接筛选 keyEventSync（RMapCache）同步器中的 key
            // 防止 redis 中手动清除，但没有清除本地缓存
            listKeys.addAll(rMapCache.keySet(keyPattern));
            if (!listKeys.isEmpty()) {
                long localCount = rMapCache.fastRemove(listKeys.toArray(new String[0]));
                log.debug("Remove from local: {}", localCount);
            }
            return;
        }

        final String[] keyArray = listKeys.toArray(new String[0]);
        RedissonHelper.getClient()
                .getKeys()
                // 1. 删除 Redis 数据（UNLINK 异步释放内存，不阻塞主线程）
                .unlinkAsync(keyArray)
                .whenCompleteAsync((count, ex) -> {
                    if (Objects.nonNull(ex))
                        log.error("Clear cache error: {}", ex.getMessage());

                    // 2. 从 keyEventSync（RMapCache）同步器中移除对应记录
                    // 即使在第 1 步中异常，这里也干脆的在同步器中移除，主要原因是本地缓存无手动清除功能，
                    // 但redis可以在 cli 或 UI 界面中手动清除，为此提供了便利性。
                    long localCount = getEventSyncCache().fastRemove(keyArray);
                    log.debug("Remove from redis: {}, local: {}", count, localCount);
                });
    }

    private static CompletionStage<Boolean> deleteAsync(String key) {
        return RedissonHelper.getInstance().deleteAsync(key)
                .whenCompleteAsync((result, ex) -> {
                    if (Objects.nonNull(ex))
                        log.error("Clear cache error: {}", ex.getMessage());

                    // 从 keyEventSync（RMapCache）同步器中移除对应记录
                    long count = getEventSyncCache().fastRemove(key);
                    log.debug("Remove count: {}", count);

                })
                .exceptionally(ex -> {
                    log.error("Clear cache error: {}", ThrowProvider.unwrapCause(ex).getMessage());
                    return Boolean.FALSE;
                });
    }

    private static <T> T inferAndConvertNull(Object value) {
        if (value instanceof String) {
            if (Objects.equals(value, RedissonHelper.NULL_MARKER))
                return null;
            else if (Objects.equals(value, RedissonHelper.EMPTY_LIST_MARKER))
                return (T) Collections.emptyList();
            else if (Objects.equals(value, RedissonHelper.EMPTY_MAP_MARKER))
                return (T) Collections.emptyMap();
            else if (Objects.equals(value, RedissonHelper.EMPTY_SET_MARKER))
                return (T) Collections.emptySet();
        }
        return (T) value;
    }

    private static RMapCache<String, String> getEventSyncCache() {
        return ContextSupport
                .getBean(CacheEventPublisher.BEAN_NAME, CacheEventPublisher.class)
                .getCacheEventListener();
    }
}
