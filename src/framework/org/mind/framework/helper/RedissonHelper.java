package org.mind.framework.helper;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.cache.CacheElement;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.cache.LruCache;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.util.DateUtils;
import org.mind.framework.util.JarFileUtils;
import org.mind.framework.util.JsonUtils;
import org.redisson.Redisson;
import org.redisson.RedissonShutdownException;
import org.redisson.api.LockOptions;
import org.redisson.api.RBucket;
import org.redisson.api.RExpirable;
import org.redisson.api.RFuture;
import org.redisson.api.RIdGenerator;
import org.redisson.api.RList;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RSet;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.config.Config;
import org.redisson.misc.CompletableFutureWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.mind.framework.web.server.WebServerConfig.JAR_IN_CLASSES;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2022/11/24
 */
@Slf4j
public class RedissonHelper {
    private static final String DEFAULT_REDISSON = "redisson.yml";
    private static final String JAR_REDISSON = String.format("%s/%s", JAR_IN_CLASSES, DEFAULT_REDISSON);
    private static final String REDIS_LOCAL_KEY = "redis_local_keys";
    public static final String LOCK_PREFIX = "LK:";
    public static final String RATE_LIMITED_PREFIX = "RL:";
    public static final String INCREMENT_PREFIX = "ICR:";
    public static final String UNIQUE_ID = "UNIQUE:ID";
    public static final String RESET_ID4DAY = "RESET:ID4DAY";
    private final RedissonClient redissonClient;
    private final Cacheable cacheable;
    private List<RedissonShutdownListener> shutdownEvents;

    private static class Helper {
        private static final RedissonHelper INSTANCE = new RedissonHelper();
    }

    private RedissonHelper() {
        log.info("Loading redisson config for: {}", DEFAULT_REDISSON);
        Config config = null;
        try {
            InputStream in;
            URL url = ClassUtils.getResource(this.getClass(), DEFAULT_REDISSON);

            if (Objects.isNull(url))
                in = JarFileUtils.getJarEntryStream(JAR_REDISSON);
            else
                in = ClassUtils.getResourceAsStream(this.getClass(), DEFAULT_REDISSON);

            // read config from file
            config = Config.fromYAML(in);
        } catch (IOException e) {
            ThrowProvider.doThrow(e);
        }

        // Used to store the key added to redis
        cacheable = LruCache.initCache();
        redissonClient = Redisson.create(config);

        Runtime.getRuntime().addShutdownHook(ExecutorFactory.newDaemonThread("Redisson-Gracefully", () -> {
            if (!redissonClient.isShutdown()) {
                log.info("Redisson-Gracefully is shutdown ....");
                if (Objects.nonNull(shutdownEvents) && !shutdownEvents.isEmpty())
                    shutdownEvents.forEach(event -> event.accept(redissonClient));

                try {
                    redissonClient.shutdown(10L, 15L, TimeUnit.SECONDS);// timeout should >= quietPeriod
                } catch (RedissonShutdownException e) {
                    log.error("Redisson shutdown exception: {}", e.getMessage());
                }
            }
        }));
    }

    public static RedissonHelper getInstance() {
        return Helper.INSTANCE;
    }

    public static RedissonClient getClient() {
        return getInstance().redissonClient;
    }

    public RedissonHelper addShutdownEvent(RedissonShutdownListener listener) {
        if (Objects.isNull(listener))
            return this;

        if (Objects.isNull(shutdownEvents))
            shutdownEvents = new ArrayList<>();

        shutdownEvents.add(listener);
        return this;
    }

    public RedissonHelper addShutdownEvent(List<RedissonShutdownListener> listeners) {
        if (Objects.isNull(listeners) || listeners.isEmpty())
            return this;

        if (Objects.isNull(shutdownEvents))
            shutdownEvents = new ArrayList<>();

        shutdownEvents.addAll(listeners);
        return this;
    }

    public <V> List<V> getList(String name) {
        return new ArrayList<>(this.rList(name));
    }

    public <V> List<V> getList(String name, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.getList(name);
        } finally {
            lock.unlock();
        }
    }

    public <V> List<V> getListWithLock(String name) {
        return this.getList(name, getReadLock(name));
    }

    public <V> RFuture<Boolean> setAsync(String name, List<V> list) {
        return this.setAsync(name, list, 0, TimeUnit.MILLISECONDS);
    }

    public <V> RFuture<Boolean> setAsync(String name, List<V> list, long expire, TimeUnit unit) {
        if (Objects.isNull(list) || list.isEmpty())
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RList<V> rList = this.rList(name);
        CompletionStage<Boolean> completionStage = rList.deleteAsync().thenApplyAsync(fn -> {
            boolean added = rList.addAll(list);
            if (added) {
                putLocal(name, list.getClass());
                setExpire(rList, expire, unit);
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        });
        return new CompletableFutureWrapper<>(completionStage);
    }

    public <V> boolean set(String name, List<V> list) {
        return this.set(name, list, 0, TimeUnit.MILLISECONDS);
    }

    public <V> boolean set(String name, List<V> list, long expire, TimeUnit unit) {
        return this.<Boolean>get(this.setAsync(name, list, expire, unit));
    }

    public <V> boolean set(String name, List<V> list, RLock lock) {
        return this.set(name, list, 0, TimeUnit.MILLISECONDS, lock);
    }

    public <V> boolean set(String name, List<V> list, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.set(name, list, expire, unit);
        } finally {
            lock.unlock();
        }
    }

    public <V> boolean setWithLock(String name, List<V> list) {
        return this.setWithLock(name, list, 0, TimeUnit.MILLISECONDS);
    }

    public <V> boolean setWithLock(String name, List<V> list, long expire, TimeUnit unit) {
        return this.set(name, list, expire, unit, this.getWriteLock(name));
    }

    public <V> RFuture<Boolean> addByListAsync(String name, V v) {
        return this.addByListAsync(name, v, 0, TimeUnit.MILLISECONDS);
    }

    public <V> RFuture<Boolean> addByListAsync(String name, V v, long expire, TimeUnit unit) {
        if (Objects.isNull(v))
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RList<V> rList = this.rList(name);
        CompletionStage<Boolean> completionStage =
                rList.isExistsAsync().thenApplyAsync(exists -> {
                    rList.add(v);
                    putLocal(name, ArrayList.class);
                    if (!exists)
                        setExpire(rList, expire, unit);
                    return Boolean.TRUE;
                });

        /*
         * Redis 7.0+
         */
//        CompletionStage<Boolean> completionStage = rList.addAsync(v).thenApplyAsync(added -> {
//            if (added) {
//                putLocal(name, ArrayList.class);
//                setExpireIfNotSet(rList, expire, unit);
//                return Boolean.TRUE;
//            }
//            return Boolean.FALSE;
//        });

        return new CompletableFutureWrapper<>(completionStage);
    }

    public <V> boolean addByList(String name, V v) {
        return this.addByList(name, v, 0, TimeUnit.MILLISECONDS);
    }

    public <V> boolean addByList(String name, V v, long expire, TimeUnit unit) {
        return this.<Boolean>get(this.addByListAsync(name, v, expire, unit));
    }

    public <V> boolean addByList(String name, V v, RLock lock) {
        return this.addByList(name, v, 0, TimeUnit.MILLISECONDS, lock);
    }

    public <V> boolean addByList(String name, V v, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.addByList(name, v, expire, unit);
        } finally {
            lock.unlock();
        }
    }

    public <V> boolean addByListWithLock(String name, V v) {
        return this.addByListWithLock(name, v, 0, TimeUnit.MILLISECONDS);
    }

    public <V> boolean addByListWithLock(String name, V v, long expire, TimeUnit unit) {
        return this.addByList(name, v, expire, unit, this.getWriteLock(name));
    }

    public RFuture<Boolean> deleteListAsync(String name) {
        RFuture<Boolean> future = this.rList(name).deleteAsync();
        this.removeLocal(name);
        return future;
    }

    public boolean deleteList(String name) {
        return this.<Boolean>get(this.deleteListAsync(name));
    }

    public boolean deleteList(String name, RLock lock) {
        lock.lock();
        try {
            return this.deleteList(name);
        } finally {
            lock.unlock();
        }
    }

    public boolean deleteListWithLock(String name) {
        return this.deleteList(name, this.getWriteLock(name));
    }

    public <V> RFuture<V> removeByListAsync(String name, int index) {
        if (index < 0)
            return new CompletableFutureWrapper<>((V) null);

        return this.<V>rList(name).removeAsync(index);
    }

    public <V> V removeByList(String name, int index) {
        return this.get(this.removeByListAsync(name, index));
    }

    public <V> V removeByList(String name, int index, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.removeByList(name, index);
        } finally {
            lock.unlock();
        }
    }

    public <V> V removeByListWithLock(String name, int index) {
        return this.removeByList(name, index, this.getWriteLock(name));
    }

    public <V> RFuture<Boolean> removeByListAsync(String name, V v) {
        if (Objects.isNull(v))
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        return this.<V>rList(name).removeAsync(v);
    }

    public <V> boolean removeByList(String name, V v) {
        return this.<Boolean>get(removeByListAsync(name, v));
    }

    public <V> boolean removeByList(String name, V v, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.removeByList(name, v);
        } finally {
            lock.unlock();
        }
    }

    public <V> boolean removeByListWithLock(String name, V v) {
        return this.removeByList(name, v, this.getWriteLock(name));
    }

    public <K, V> V getMapValue(String name, K k) {
        return this.<K, V>rMap(name).get(k);
    }

    public <K, V> V getMapValue(String name, K k, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.getMapValue(name, k);
        } finally {
            lock.unlock();
        }
    }

    public <K, V> V getMapValueWithLock(String name, K k) {
        return this.getMapValue(name, k, getReadLock(name));
    }

    public <K, V> Map<K, V> getMap(String name) {
        return new HashMap<>(this.rMap(name));
    }

    public <K, V> Map<K, V> getMap(String name, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.getMap(name);
        } finally {
            lock.unlock();
        }
    }

    public <K, V> Map<K, V> getMapWithLock(String name) {
        return this.getMap(name, getReadLock(name));
    }

    public <K, V> RFuture<Boolean> setAsync(String name, Map<K, V> map) {
        return this.setAsync(name, map, 0, TimeUnit.MILLISECONDS);
    }

    public <K, V> RFuture<Boolean> setAsync(String name, Map<K, V> map, long expire, TimeUnit unit) {
        if (Objects.isNull(map) || map.isEmpty())
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RMap<K, V> rMap = this.rMap(name);
        CompletionStage<Boolean> completionStage = rMap.deleteAsync().thenApplyAsync(deleted -> {
            rMap.putAll(map);
            putLocal(name, map.getClass());
            setExpire(rMap, expire, unit);
            return Boolean.TRUE;
        });

        return new CompletableFutureWrapper<>(completionStage);
    }

    public <K, V> boolean set(String name, Map<K, V> map) {
        return this.set(name, map, 0, TimeUnit.MILLISECONDS);
    }

    public <K, V> boolean set(String name, Map<K, V> map, long expire, TimeUnit unit) {
        return this.<Boolean>get(setAsync(name, map, expire, unit));
    }

    public <K, V> boolean set(String name, Map<K, V> map, RLock lock) {
        return this.set(name, map, 0, TimeUnit.MILLISECONDS, lock);
    }

    public <K, V> boolean set(String name, Map<K, V> map, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.set(name, map, expire, unit);
        } finally {
            lock.unlock();
        }
    }

    public <K, V> boolean setWithLock(String name, Map<K, V> map) {
        return this.setWithLock(name, map, 0, TimeUnit.MILLISECONDS);
    }

    public <K, V> boolean setWithLock(String name, Map<K, V> map, long expire, TimeUnit unit) {
        return set(name, map, expire, unit, this.getWriteLock(name));
    }

    public <K, V> RFuture<Boolean> putByMapAsync(String name, K k, V v) {
        return this.putByMapAsync(name, k, v, 0, TimeUnit.MILLISECONDS);
    }

    public <K, V> RFuture<Boolean> putByMapAsync(String name, K k, V v, long expire, TimeUnit unit) {
        if (Objects.isNull(k))
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RMap<K, V> rMap = this.rMap(name);
        CompletionStage<Boolean> completionStage =
                rMap.isExistsAsync().thenApplyAsync(exists -> {
                    boolean puted = rMap.fastPut(k, v);
                    if (puted) {
                        putLocal(name, HashMap.class);
                        if (!exists)
                            setExpire(rMap, expire, unit);
                        return Boolean.TRUE;
                    }
                    return Boolean.FALSE;
                });
        return new CompletableFutureWrapper<>(completionStage);
    }

    public <K, V> boolean putByMap(String name, K k, V v) {
        return this.putByMap(name, k, v, 0, TimeUnit.MILLISECONDS);
    }

    public <K, V> boolean putByMap(String name, K k, V v, long expire, TimeUnit unit) {
        return this.<Boolean>get(putByMapAsync(name, k, v, expire, unit));
    }

    public <K, V> boolean putByMap(String name, K k, V v, RLock lock) {
        return this.putByMap(name, k, v, 0, TimeUnit.MILLISECONDS, lock);
    }

    public <K, V> boolean putByMap(String name, K k, V v, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.putByMap(name, k, v, expire, unit);
        } finally {
            lock.unlock();
        }
    }

    public <K, V> boolean putByMapWithLock(String name, K k, V v) {
        return this.putByMapWithLock(name, k, v, 0, TimeUnit.MILLISECONDS);
    }

    public <K, V> boolean putByMapWithLock(String name, K k, V v, long expire, TimeUnit unit) {
        return this.putByMap(name, k, v, expire, unit, this.getWriteLock(name));
    }

    public <K, V> RFuture<Boolean> replaceByMapAsync(String name, K k, V v) {
        if (Objects.isNull(k))
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        return this.<K, V>rMap(name).fastReplaceAsync(k, v);
    }

    public <K, V> boolean replaceByMap(String name, K k, V v) {
        return this.<Boolean>get(replaceByMapAsync(name, k, v));
    }

    public <K, V> boolean replaceByMap(String name, K k, V v, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.replaceByMap(name, k, v);
        } finally {
            lock.unlock();
        }
    }

    public <K, V> boolean replaceByMapWithLock(String name, K k, V v) {
        return this.replaceByMap(name, k, v, this.getWriteLock(name));
    }

    public RFuture<Boolean> deleteMapAsync(String name) {
        RFuture<Boolean> future = this.rMap(name).deleteAsync();
        this.removeLocal(name);
        return future;
    }

    public boolean deleteMap(String name) {
        return this.<Boolean>get(deleteMapAsync(name));
    }

    public boolean deleteMap(String name, RLock lock) {
        lock.lock();
        try {
            return this.deleteMap(name);
        } finally {
            lock.unlock();
        }
    }

    public boolean deleteMapWithLock(String name) {
        return this.deleteMap(name, this.getWriteLock(name));
    }

    @SafeVarargs
    public final <K> RFuture<Long> removeByMapAsync(String name, K... k) {
        if (Objects.isNull(k))
            return new CompletableFutureWrapper<>(0L);

        return this.rMap(name).fastRemoveAsync(k);
    }

    public <K> long removeByMap(String name, K k) {
        return this.<Long>get(removeByMapAsync(name, k));
    }

    public <K> long removeByMap(String name, K k, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.removeByMap(name, k);
        } finally {
            lock.unlock();
        }
    }

    public <K> long removeByMapWithLock(String name, K k) {
        return this.removeByMap(name, k, this.getWriteLock(name));
    }

    public <V> Set<V> getSet(String name) {
        return new HashSet<>(this.rSet(name));
    }

    public <V> Set<V> getSet(String name, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.getSet(name);
        } finally {
            lock.unlock();
        }
    }

    public <V> Set<V> getSetWithLock(String name) {
        return this.getSet(name, getReadLock(name));
    }

    public <V> RFuture<Boolean> setAsync(String name, Set<V> set) {
        return this.setAsync(name, set, 0, TimeUnit.MILLISECONDS);
    }

    public <V> RFuture<Boolean> setAsync(String name, Set<V> set, long expire, TimeUnit unit) {
        if (Objects.isNull(set) || set.isEmpty())
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RSet<V> rSet = this.rSet(name);
        CompletionStage<Boolean> completionStage = rSet.deleteAsync().thenApplyAsync(deleted -> {
            boolean added = rSet.addAll(set);
            if (added) {
                putLocal(name, set.getClass());
                setExpire(rSet, expire, unit);
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        });

        return new CompletableFutureWrapper<>(completionStage);
    }

    public <V> boolean set(String name, Set<V> set) {
        return set(name, set, 0, TimeUnit.MILLISECONDS);
    }

    public <V> boolean set(String name, Set<V> set, long expire, TimeUnit unit) {
        return this.<Boolean>get(setAsync(name, set, expire, unit));
    }

    public <V> boolean set(String name, Set<V> set, RLock lock) {
        return set(name, set, 0, TimeUnit.MILLISECONDS, lock);
    }

    public <V> boolean set(String name, Set<V> set, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.set(name, set, expire, unit);
        } finally {
            lock.unlock();
        }
    }

    public <V> boolean setWithLock(String name, Set<V> set) {
        return setWithLock(name, set, 0, TimeUnit.MILLISECONDS);
    }

    public <V> boolean setWithLock(String name, Set<V> set, long expire, TimeUnit unit) {
        return this.set(name, set, expire, unit, this.getWriteLock(name));
    }

    public <V> RFuture<Boolean> addBySetAsync(String name, V v) {
        return addBySetAsync(name, v, 0, TimeUnit.MILLISECONDS);
    }

    public <V> RFuture<Boolean> addBySetAsync(String name, V v, long expire, TimeUnit unit) {
        if (Objects.isNull(v))
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RSet<V> rSet = this.rSet(name);
        CompletionStage<Boolean> completionStage =
                rSet.isExistsAsync().thenApplyAsync(exists -> {
                    boolean added = rSet.add(v);
                    if (added) {
                        putLocal(name, HashSet.class);
                        if (!exists)
                            setExpire(rSet, expire, unit);
                        return Boolean.TRUE;
                    }
                    return Boolean.FALSE;
                });

        return new CompletableFutureWrapper<>(completionStage);
    }

    public <V> boolean addBySet(String name, V v) {
        return addBySet(name, v, 0, TimeUnit.MILLISECONDS);
    }

    public <V> boolean addBySet(String name, V v, long expire, TimeUnit unit) {
        return this.<Boolean>get(addBySetAsync(name, v, expire, unit));
    }

    public <V> boolean addBySet(String name, V v, RLock lock) {
        return addBySet(name, v, 0, TimeUnit.MILLISECONDS, lock);
    }

    public <V> boolean addBySet(String name, V v, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.addBySet(name, v, expire, unit);
        } finally {
            lock.unlock();
        }
    }

    public <V> boolean addBySetWithLock(String name, V v) {
        return addBySetWithLock(name, v, 0, TimeUnit.MILLISECONDS);
    }

    public <V> boolean addBySetWithLock(String name, V v, long expire, TimeUnit unit) {
        return this.addBySet(name, v, expire, unit, this.getWriteLock(name));
    }

    public RFuture<Boolean> deleteSetAsync(String name) {
        RFuture<Boolean> future = this.rSet(name).deleteAsync();
        this.removeLocal(name);
        return future;
    }

    public boolean deleteSet(String name) {
        return this.<Boolean>get(deleteSetAsync(name));
    }

    public boolean deleteSet(String name, RLock lock) {
        lock.lock();
        try {
            return this.deleteSet(name);
        } finally {
            lock.unlock();
        }
    }

    public boolean deleteSetWithLock(String name) {
        return this.deleteSet(name, this.getWriteLock(name));
    }

    public <V> RFuture<Boolean> removeBySetAsync(String name, V v) {
        return this.rSet(name).removeAsync(v);
    }

    public <V> boolean removeBySet(String name, V v) {
        return this.<Boolean>get(removeBySetAsync(name, v));
    }

    public <V> boolean removeBySet(String name, V v, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.removeBySet(name, v);
        } finally {
            lock.unlock();
        }
    }

    public <V> boolean removeBySetWithLock(String name, V v) {
        return this.removeBySet(name, v, this.getWriteLock(name));
    }

    public <V> V get(String name) {
        return getClient().<V>getBucket(name).get();
    }

    public <V> V getWithLock(String name) {
        return this.get(name, this.getReadLock(name));
    }

    public <V> V get(String name, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.get(name);
        } finally {
            lock.unlock();
        }
    }

    public RFuture<Boolean> deleteAsync(String name) {
        RFuture<Boolean> future = getClient().getBucket(name).deleteAsync();
        this.removeLocal(name);
        return future;
    }

    public boolean delete(String name) {
        return this.<Boolean>get(deleteAsync(name));
    }

    public boolean delete(String name, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.delete(name);
        } finally {
            lock.unlock();
        }
    }

    public boolean deleteWithLock(String name) {
        return this.delete(name, this.getWriteLock(name));
    }

    public <V> RFuture<Void> setAsync(String name, V value) {
        return setAsync(name, value, 0, TimeUnit.MILLISECONDS);
    }

    public <V> RFuture<Void> setAsync(String name, V value, long expire, TimeUnit unit) {
        RBucket<V> rBucket = getClient().getBucket(name);
        RFuture<Void> future = expire > 0 ?
                rBucket.setAsync(value, expire, unit) :
                rBucket.setAsync(value);
        this.putLocal(name, value.getClass());
        return future;
    }

    public <V> void set(String name, V value) {
        set(name, value, 0, TimeUnit.MILLISECONDS);
    }

    public <V> void set(String name, V value, long expire, TimeUnit unit) {
        this.get(setAsync(name, value, expire, unit));
    }

    public <V> void setWithLock(String name, V value) {
        setWithLock(name, value, 0, TimeUnit.MILLISECONDS);
    }

    public <V> void setWithLock(String name, V value, long expire, TimeUnit unit) {
        this.set(name, value, expire, unit, this.getWriteLock(name));
    }

    public <V> void set(String name, V value, RLock lock) {
        set(name, value, 0, TimeUnit.MILLISECONDS, lock);
    }

    public <V> void set(String name, V value, long expire, TimeUnit unit, RLock Lock) {
        // activating watch-dog
        Lock.lock();
        try {
            this.set(name, value, expire, unit);
        } finally {
            Lock.unlock();
        }
    }

    public long getIdForDate() {
        return getIdForDate(ZoneId.systemDefault());
    }

    public long getIdForDate(ZoneId zone) {
        return getIdForDate(zone, DateUtils.SIMPLE_DATE_PATTERN);
    }

    public long getIdForDate(ZoneId zone, String dateFormat) {
        String date = DateUtils.dateNow(zone).format(DateTimeFormatter.ofPattern(dateFormat));
        long generateId = getId4Day(zone) / 10_000L;
        StringBuilder joiner = new StringBuilder();

        int length = 6 - String.valueOf(generateId).length();
        if (length > 0) {
            for (int i = 0; i < length; ++i)
                joiner.append("0");
        }

        if (joiner.length() > 0)
            return Long.parseLong(String.format("%s%s%d", date, joiner, generateId));

        return Long.parseLong(String.format("%s%d", date, generateId));
    }

    public long getId() {
        return getId(100_000L, 10_000L);
    }

    public long getId(long start, long allocationSize) {
        RIdGenerator idGenerator = getClient().getIdGenerator(UNIQUE_ID);
        idGenerator.isExistsAsync().whenComplete((exists, ex) -> {
            if (!exists) {
                try {
                    idGenerator.tryInit(start, allocationSize);
                } catch (Exception ignored) {
                }
            }
        });

        long generateId = idGenerator.nextId() / 10_000L;
        StringBuilder joiner = new StringBuilder();

        int length = 6 - String.valueOf(generateId).length();
        if (length > 0) {
            joiner.append("1");
            for (int i = 1; i < length; ++i)
                joiner.append("0");
        }

        joiner.append(generateId);
        return Long.parseLong(joiner.toString());
    }

    public RRateLimiter getRateLimiter(String name, long rate, long intervalSeconds) {
        RRateLimiter rLimiter = getClient().getRateLimiter(RATE_LIMITED_PREFIX + name);
        rLimiter.isExistsAsync().whenComplete((exists, ex) -> {
            if (!exists)
                rLimiter.trySetRate(RateType.OVERALL, rate, intervalSeconds, RateIntervalUnit.SECONDS);
        });
        return rLimiter;
    }

    public RLock getLock(String name) {
        return getClient().getLock(LOCK_PREFIX + name);
    }

    public RReadWriteLock getReadWriteLock(String name) {
        return getClient().getReadWriteLock(LOCK_PREFIX + name);
    }

    public RLock getReadLock(String name) {
        return this.getReadWriteLock(name).readLock();
    }

    public RLock getWriteLock(String name) {
        return this.getReadWriteLock(name).writeLock();
    }

    public RLock getFairLock(String name) {
        return getClient().getFairLock(LOCK_PREFIX + name);
    }

    public RLock getSpinLock(String name) {
        return getClient().getSpinLock(LOCK_PREFIX + name);
    }

    public RLock getSpinLock(String name, LockOptions.BackOff backOff) {
        return getClient().getSpinLock(LOCK_PREFIX + name, backOff);
    }

    public void removeContainsKeys(String keyPart, boolean... useLock) {
        CacheElement element = cacheable.getCache(REDIS_LOCAL_KEY);
        if (Objects.isNull(element))
            return;

        Map<String, Class<?>> redisKeys = (Map<String, Class<?>>) element.getValue();
        if (Objects.isNull(redisKeys) || redisKeys.isEmpty())
            return;

        final boolean lock = ArrayUtils.isNotEmpty(useLock) && useLock[0];
        redisKeys.forEach((k, v) -> {
            if (StringUtils.contains(k, keyPart)) {
                if (List.class.isAssignableFrom(v)) {
                    if (lock)
                        this.deleteListWithLock(k);
                    else
                        this.deleteListAsync(k);
                } else if (Map.class.isAssignableFrom(v)) {
                    if (lock)
                        this.deleteMapWithLock(k);
                    else
                        this.deleteMapAsync(k);
                } else if (Set.class.isAssignableFrom(v)) {
                    if (lock)
                        this.deleteSetWithLock(k);
                    else
                        this.deleteSetAsync(k);
                } else {
                    if (lock)
                        this.deleteWithLock(k);
                    else
                        this.deleteAsync(k);
                }
            }
        });
    }

    public List<String> getLocalKeys(Class<?> clazzType) {
        CacheElement element = cacheable.getCache(REDIS_LOCAL_KEY);

        if (Objects.isNull(element))
            return Collections.emptyList();

        Map<String, Class<?>> redisKeys =
                (Map<String, Class<?>>) element.getValue();

        if (Objects.isNull(redisKeys) || redisKeys.isEmpty())
            return Collections.emptyList();

        List<String> results = new ArrayList<>();
        redisKeys.forEach((k, v) -> {
            if (v.getName().equals(clazzType.getName()))
                results.add(k);
        });

        return results;
    }

    public Map<String, Class<?>> getLocalKeys(String keyPart) {
        CacheElement element = cacheable.getCache(REDIS_LOCAL_KEY);

        if (Objects.isNull(element))
            return Collections.emptyMap();

        Map<String, Class<?>> redisKeys =
                (Map<String, Class<?>>) element.getValue();

        if (Objects.isNull(redisKeys) || redisKeys.isEmpty())
            return Collections.emptyMap();

        Map<String, Class<?>> results = new HashMap<>();
        redisKeys.forEach((k, v) -> {
            if (k.contains(keyPart))
                results.put(k, v);
        });

        return results;
    }

    public Map<String, Class<?>> getLocalKeys() {
        CacheElement element = cacheable.getCache(REDIS_LOCAL_KEY);

        if (Objects.isNull(element))
            return Collections.emptyMap();

        Map<String, Class<?>> keysMap =
                (Map<String, Class<?>>) element.getValue();

        if (Objects.isNull(keysMap) || keysMap.isEmpty())
            return Collections.emptyMap();

        return new HashMap<>(keysMap);
    }

    public boolean setExpire(RExpirable rExpirable, long expire, TimeUnit unit) {
        return this.<Boolean>get(this.setExpireAsync(rExpirable, expire, unit));
    }

    public RFuture<Boolean> setExpireAsync(RExpirable rExpirable, long expire, TimeUnit unit) {
        if (expire > 0L) {
            long newExpire = expire;
            if (TimeUnit.MILLISECONDS != unit)
                newExpire = unit.toMillis(expire);
            return rExpirable.expireAsync(Duration.ofMillis(newExpire));
        }
        return new CompletableFutureWrapper<>(Boolean.FALSE);
    }

    /**
     * Redis 7.0+
     */
    public boolean setExpireIfNotSet(RExpirable rExpirable, long expire, TimeUnit unit) {
        return this.<Boolean>get(this.setExpireIfNotSetAsync(rExpirable, expire, unit));
    }

    /**
     * Redis 7.0+
     */
    public RFuture<Boolean> setExpireIfNotSetAsync(RExpirable rExpirable, long expire, TimeUnit unit) {
        if (expire > 0L) {
            long newExpire = expire;
            if (TimeUnit.MILLISECONDS != unit)
                newExpire = unit.toMillis(expire);

            // Redis 7.0+
            return rExpirable.expireIfNotSetAsync(Duration.ofMillis(newExpire));
        }

        return new CompletableFutureWrapper<>(Boolean.FALSE);
    }

    /**
     * see #org.redisson.command.CommandAsyncService#get
     */
    public <V> V get(RFuture<V> future) {
        try {
            return future.toCompletableFuture().get();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RedisException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RedisException)
                throw (RedisException) e.getCause();

            throw new RedisException("Unexpected exception while processing command", e.getCause());
        }
    }

    public Boolean getBooleanByStage(CompletionStage<Boolean> stage) {
        CompletableFuture<Boolean> future = stage.toCompletableFuture();
        try {
            return future.get();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            log.error(e.getMessage(), e);
        } catch (ExecutionException e) {
            log.error(e.getMessage(), e);
        }
        return Boolean.FALSE;
    }

    private void removeLocal(String name) {
        CacheElement element = cacheable.getCache(REDIS_LOCAL_KEY);
        if (Objects.isNull(element))
            return;

        Map<String, Class<?>> localKeys = (Map<String, Class<?>>) element.getValue();
        if (Objects.isNull(localKeys) || localKeys.isEmpty())
            return;

        localKeys.remove(name);
    }

    private void putLocal(String name, Class<?> clazzType) {
        CacheElement element = cacheable.getCache(REDIS_LOCAL_KEY);
        if (Objects.isNull(element)) {
            Map<String, Class<?>> redisKeys = new ConcurrentHashMap<>();
            redisKeys.put(name, clazzType);
            cacheable.addCache(REDIS_LOCAL_KEY, redisKeys, true);
            return;
        }

        Map<String, Class<?>> redisKeys =
                (Map<String, Class<?>>) element.getValue();
        redisKeys.putIfAbsent(name, clazzType);
    }

    private <V> RList<V> rList(String name) {
        return getClient().getList(name);
    }

    private <K, V> RMap<K, V> rMap(String name) {
        return getClient().getMap(name);
    }

    private <V> RSet<V> rSet(String name) {
        return getClient().getSet(name);
    }

    private long getId4Day(ZoneId zone) {
        String currentDate =
                DateUtils.format(
                        DateUtils.CachedTime.currentMillis(),
                        DateUtils.FULL_DATE_PATTERN,
                        TimeZone.getTimeZone(zone));

        String idKey = String.join(JsonUtils.COLON_SEPARATOR, UNIQUE_ID, currentDate);
        RIdGenerator idGenerator = getClient().getIdGenerator(idKey);
        String reset4day = get(RESET_ID4DAY);
        if (StringUtils.isEmpty(reset4day) || !currentDate.equals(reset4day))
            resetId4Day(idGenerator, currentDate, zone);

        return idGenerator.nextId();
    }

    private void resetId4Day(RIdGenerator idGenerator, String currentDate, ZoneId zone) {
        // Time difference in seconds from midnight
        Duration duration = DateUtils.endOfRemaining(DateUtils.dateTimeNow(zone), LocalTime.MIDNIGHT);
        try {
            idGenerator.tryInit(100_000L, 10_000L);
            idGenerator.expireAsync(duration);
        } catch (Exception ignored) {
        }

        try {
            setAsync(RESET_ID4DAY, currentDate, duration.getSeconds(), TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }
}
