package org.mind.framework.cache;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.ContextSupport;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.service.Cloneable;
import org.redisson.api.RLock;
import org.redisson.api.RMapCache;
import org.redisson.api.RType;
import org.redisson.api.options.KeysScanOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * @author Marcus
 * @version 1.0
 * @date 2026/5/23
 */
@Slf4j
public final class CacheUtils {
    private static final Supplier<?> NO_OP = () -> null;
    public static final long WAIT_TIME = 5L;
    public static final String BACKFILL_CACHE = ":backfill:cache";

    public static <T> T getOnly(String key, Cacheable cacheable) {
        return (T) getOnly(key, cacheable, NO_OP);
    }

    public static <T> T getOnly(String key, Cacheable cacheable, Supplier<T> action) {
        // L1
        if (cacheable != null) {
            CacheElement element = cacheable.getCache(key);

            if (Objects.nonNull(element))
                return inferAndConvertNull(element.getValue());
        }

        // L2
        Object fromRedis = readFromRedis(key);
        if (fromRedis != null)
            return inferAndConvertNull(fromRedis);

        return action.get();
    }

    public static <T> T getAndSet(String key, Cacheable cacheable, Duration duration, Supplier<T> action) {
        return getAndSet(key, cacheable, duration, false, action);
    }

    public static <T> T getAndSet(String key, Cacheable cacheable, Duration duration, boolean cacheNull, Supplier<T> loader) {
        // L1
        if (cacheable != null) {
            CacheElement element = cacheable.getCache(key);

            if (Objects.nonNull(element))
                return inferAndConvertNull(element.getValue());
        }

        // exists redisson config
        try {
            RedissonHelper.getInstance();
        } catch (Exception e) {
            // Non-Redisson, backfill to L1
            log.warn("Redisson unavailable: {}", e.getMessage());
            T result = loader.get();
            backfillL1(key, cacheable, result, Math.max(duration.toMillis(), 0L), cacheNull);
            return result;
        }

        // L2
        Object fromRedis = readFromRedis(key);
        if (fromRedis != null) {
            backfillL1(key, cacheable, fromRedis, cacheNull);
            return inferAndConvertNull(fromRedis);
        }

        return loadWithGuard(
                key,
                WAIT_TIME,
                cacheable,
                cacheNull,
                loader,
                () -> readFromRedis(key),
                (k, value) -> backfill(k, cacheable, value, Math.max(duration.toMillis(), 0L), cacheNull),
                () -> {
                    T result = loader.get();
                    backfillL1(key, cacheable, result, Math.max(duration.toMillis(), 0L), cacheNull);
                    return result;
                }
        );
    }

    public static <T> T loadWithGuard(String key,
                                      long waitTimeSecs,
                                      Cacheable cacheable,
                                      boolean cacheNull,
                                      Supplier<T> loader,
                                      Supplier<Object> l2Reader,
                                      BiConsumer<String, Object> writer,
                                      Supplier<T> degradeLoader) {

        RLock lock = RedissonHelper.getInstance().getLock(key + BACKFILL_CACHE);
        try {
            if (lock.tryLock(waitTimeSecs, TimeUnit.SECONDS)) {
                try {
                    Object again = l2Reader.get();
                    if (Objects.nonNull(again)) {
                        backfillL1(key, cacheable, again, cacheNull);
                        return inferAndConvertNull(again);
                    }

                    T result = loader.get();
                    writer.accept(key, result);
                    return result;
                } finally {
                    if (lock.isHeldByCurrentThread())
                        lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while loading cache: " + key, e);
        }

        // 降级：pre-read → 本地回源
        Object again = l2Reader.get();
        if (Objects.nonNull(again)) {
            backfillL1(key, cacheable, again, cacheNull);
            return inferAndConvertNull(again);
        }

        log.warn("Cache load lock timeout({} s), degrade to local load: {}",
                waitTimeSecs, key);

        // degrade to L1-only
        return degradeLoader.get();
    }

    public static boolean isEmpty(Object value) {
        if (Objects.isNull(value))
            return true;

        if (value instanceof Collection)
            return ((Collection<?>) value).isEmpty();

        if (value instanceof Map)
            return ((Map<?, ?>) value).isEmpty();

        if (value instanceof String)
            return StringUtils.isEmpty((String) value);

        if (value.getClass().isArray())
            return ArrayUtils.getLength(value) == 0;

        return false;
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
            RMapCache<String, String> rMapCache = getCacheEventPublisher().getCacheEventListener();
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
                    long localCount = getCacheEventPublisher().getCacheEventListener().fastRemove(keyArray);
                    log.debug("Remove from redis: {}, local: {}", count, localCount);
                });
    }

    private static CompletionStage<Boolean> deleteAsync(String key) {
        return RedissonHelper.getInstance().deleteAsync(key)
                .whenCompleteAsync((result, ex) -> {
                    if (Objects.nonNull(ex))
                        log.error("Clear cache error: {}", ex.getMessage());

                    // 从 keyEventSync（RMapCache）同步器中移除对应记录
                    long count = getCacheEventPublisher().getCacheEventListener().fastRemove(key);
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

    private static CacheEventPublisher getCacheEventPublisher() {
        return ContextSupport.getBean(CacheEventPublisher.BEAN_NAME, CacheEventPublisher.class);
    }

    private static void backfillL1(String key, Cacheable cacheable, Object value, boolean cacheNull) {
        if (cacheable == null || (!cacheNull && isEmpty(value)))
            return;

        long expireMs = RedissonHelper.getInstance().rBucket(key).remainTimeToLive();
        if (expireMs >= -1) {
            cacheable.addCache(key, new CacheElement(
                    cacheNull ? wrapEmpty(value) : value,
                    key,
                    Math.max(expireMs, 0L),
                    Cloneable.CloneType.NONE)
            );
        }
    }

    private static void backfillL1(String key, Cacheable cacheable, Object value, long expireMs, boolean cacheNull) {
        if (cacheable == null || (!cacheNull && isEmpty(value)))
            return;

        cacheable.addCache(key, new CacheElement(
                cacheNull ? wrapEmpty(value) : value,
                key,
                expireMs,
                Cloneable.CloneType.NONE)
        );
    }

    private static void backfill(String key, Cacheable cacheable, Object value, long expireMs, boolean cacheNull) {
        // L2
        if (cacheNull || !isEmpty(value)) {
            Object wrapValue = cacheNull ? wrapEmpty(value) : value;
            long redisExpireMs = expireMs == 0L ? -1L : expireMs;

            if (wrapValue instanceof List)
                RedissonHelper.getInstance().set(key, (List<?>) wrapValue, redisExpireMs, TimeUnit.MILLISECONDS);
            else if (wrapValue instanceof Set)
                RedissonHelper.getInstance().set(key, (Set<?>) wrapValue, redisExpireMs, TimeUnit.MILLISECONDS);
            else if (wrapValue instanceof Map)
                RedissonHelper.getInstance().set(key, (Map<?, ?>) wrapValue, redisExpireMs, TimeUnit.MILLISECONDS);
            else
                RedissonHelper.getInstance().set(key, wrapValue, redisExpireMs, TimeUnit.MILLISECONDS);

            // publish cache event
            getCacheEventPublisher().publish(key, expireMs, TimeUnit.MILLISECONDS);
        }

        // L1
        backfillL1(key, cacheable, value, expireMs, cacheNull);
    }

    public static Object readFromRedis(String key) {
        RType rType = null;
        try {
            rType = RedissonHelper.getClient()
                    .getKeys()
                    .getType(key);
        } catch (Exception ignored) {}

        if (Objects.isNull(rType))
            return null;

        return switch (rType) {
            case MAP -> RedissonHelper.getInstance().getMapWithLock(key);
            case SET -> RedissonHelper.getInstance().getSetWithLock(key);
            case LIST -> RedissonHelper.getInstance().getListWithLock(key);
            case OBJECT -> RedissonHelper.getInstance().getWithLock(key);
            default -> null;
        };
    }

    private static Object wrapEmpty(Object value) {
        if (value == null)
            return RedissonHelper.NULL_MARKER;

        if (value instanceof List && ((List<?>) value).isEmpty())
            return RedissonHelper.EMPTY_LIST_MARKER;

        if (value instanceof Map && ((Map<?, ?>) value).isEmpty())
            return RedissonHelper.EMPTY_MAP_MARKER;

        if (value instanceof Set && ((Set<?>) value).isEmpty())
            return RedissonHelper.EMPTY_SET_MARKER;

        return value;
    }

}