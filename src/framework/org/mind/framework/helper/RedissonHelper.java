package org.mind.framework.helper;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.cache.CacheElement;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.cache.LruCache;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.util.DateUtils;
import org.mind.framework.util.JarFileUtils;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    public <V> RFuture<Boolean> setAsync(String name, List<V> list, long expire, TimeUnit unit) {
        if (Objects.isNull(list) || list.isEmpty())
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RList<V> rList = this.rList(name);
        CompletionStage<Boolean> completionStage = rList.deleteAsync().thenApplyAsync(fn -> {
            RFuture<Boolean> future = rList.addAllAsync(list);
            this.setExpireAsync(rList, expire, unit);
            this.putLocal(name, list.getClass());
            return this.<Boolean>get(future);
        });
        return new CompletableFutureWrapper<>(completionStage);
    }

    public <V> boolean set(String name, List<V> list, long expire, TimeUnit unit) {
        return this.<Boolean>get(this.setAsync(name, list, expire, unit));
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

    public <V> boolean setWithLock(String name, List<V> list, long expire, TimeUnit unit) {
        return this.set(name, list, expire, unit, this.getWriteLock(name));
    }

    public <V> RFuture<Boolean> addByListAsync(String name, V v, long expire, TimeUnit unit) {
        if (Objects.isNull(v))
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RList<V> rList = this.rList(name);
        this.setExpireIfNotSetAsync(rList, expire, unit);
        RFuture<Boolean> future = rList.addAsync(v);
        this.putLocal(name, ArrayList.class);
        return future;
    }

    public <V> boolean addByList(String name, V v, long expire, TimeUnit unit) {
        return this.<Boolean>get(this.addByListAsync(name, v, expire, unit));
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

    public <K, V> RFuture<Boolean> setAsync(String name, Map<K, V> map, long expire, TimeUnit unit) {
        if (Objects.isNull(map) || map.isEmpty())
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RMap<K, V> rMap = this.rMap(name);
        CompletionStage<Boolean> completionStage = rMap.deleteAsync().thenApplyAsync(fn -> {
            rMap.putAllAsync(map);
            this.setExpireAsync(rMap, expire, unit);
            this.putLocal(name, map.getClass());
            return Boolean.TRUE;
        });

        return new CompletableFutureWrapper<>(completionStage);
    }

    public <K, V> boolean set(String name, Map<K, V> map, long expire, TimeUnit unit) {
        return this.<Boolean>get(setAsync(name, map, expire, unit));
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

    public <K, V> boolean setWithLock(String name, Map<K, V> map, long expire, TimeUnit unit) {
        return set(name, map, expire, unit, this.getWriteLock(name));
    }

    public <K, V> RFuture<Boolean> putByMapAsync(String name, K k, V v, long expire, TimeUnit unit) {
        if (Objects.isNull(k))
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RMap<K, V> rMap = this.rMap(name);
        this.setExpireIfNotSetAsync(rMap, expire, unit);
        RFuture<Boolean> future = rMap.fastPutAsync(k, v);
        this.putLocal(name, HashMap.class);
        return future;
    }

    public <K, V> boolean putByMap(String name, K k, V v, long expire, TimeUnit unit) {
        return this.<Boolean>get(putByMapAsync(name, k, v, expire, unit));
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

    public <K> RFuture<Long> removeByMapAsync(String name, K... k) {
        if (Objects.isNull(k))
            return new CompletableFutureWrapper<>(0L);

        return this.rMap(name).fastRemoveAsync(k);
    }


    public <K> long removeByMap(String name, K k) {
        return this.<Long>get(removeByMapAsync(name, k));
    }

    public <K> long removeByMap(String name, K k, RLock lock) {
        // activating watch-dosg
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

    public <V> RFuture<Boolean> setAsync(String name, Set<V> set, long expire, TimeUnit unit) {
        if (Objects.isNull(set) || set.isEmpty())
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RSet<V> rSet = this.rSet(name);
        CompletionStage<Boolean> completionStage = rSet.deleteAsync().thenApplyAsync(fn -> {
            rSet.addAllAsync(set);
            this.setExpireAsync(rSet, expire, unit);
            this.putLocal(name, set.getClass());
            return Boolean.TRUE;
        });

        return new CompletableFutureWrapper<>(completionStage);
    }

    public <V> boolean set(String name, Set<V> set, long expire, TimeUnit unit) {
        return this.<Boolean>get(setAsync(name, set, expire, unit));
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

    public <V> boolean setWithLock(String name, Set<V> set, long expire, TimeUnit unit) {
        return this.set(name, set, expire, unit, this.getWriteLock(name));
    }

    public <V> RFuture<Boolean> addBySetAsync(String name, V v, long expire, TimeUnit unit) {
        if (Objects.isNull(v))
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RSet<V> rSet = this.rSet(name);
        this.setExpireIfNotSetAsync(rSet, expire, unit);
        RFuture<Boolean> future = rSet.addAsync(v);
        this.putLocal(name, HashSet.class);
        return future;
    }

    public <V> boolean addBySet(String name, V v, long expire, TimeUnit unit) {
        return this.<Boolean>get(addBySetAsync(name, v, expire, unit));
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

    public <V> RFuture<Void> setAsync(String name, V value, long expire, TimeUnit unit) {
        RBucket<V> rBucket = getClient().getBucket(name);
        RFuture<Void> future = expire > 0 ?
                rBucket.setAsync(value, expire, unit) :
                rBucket.setAsync(value);
        this.putLocal(name, value.getClass());
        return future;
    }

    public <V> void set(String name, V value, long expire, TimeUnit unit) {
        this.get(setAsync(name, value, expire, unit));
    }

    public <V> void setWithLock(String name, V value, long expire, TimeUnit unit) {
        this.set(name, value, expire, unit, this.getWriteLock(name));
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
        return Long.parseLong(String.format("%s%d", date, getId()));
    }

    public long getId() {
        return getId(100_000L, 1_000L);
    }

    public long getId(long start, long allocationSize) {
        RIdGenerator idGenerator = getClient().getIdGenerator(UNIQUE_ID);
        idGenerator.isExistsAsync().whenComplete((exists, ex) -> {
            if (!exists) {
                try {
                    idGenerator.tryInit(start, allocationSize);
                } catch (Exception ignored) {}
            }
        });
        return idGenerator.nextId();
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

    public void removeContainsFromLocalKeys(String keyPart) {
        CacheElement element = cacheable.getCache(REDIS_LOCAL_KEY);
        if (Objects.isNull(element))
            return;

        Map<String, Class<?>> redisKeys =
                (Map<String, Class<?>>) element.getValue();
        if (Objects.isNull(redisKeys) || redisKeys.isEmpty())
            return;

        redisKeys.forEach((k, v) -> {
            if (StringUtils.contains(k, keyPart)) {
                if (List.class.isAssignableFrom(v))
                    this.deleteListAsync(k);
                else if (Map.class.isAssignableFrom(v))
                    this.deleteMapAsync(k);
                else if (Set.class.isAssignableFrom(v))
                    this.deleteSetAsync(k);
                else
                    this.deleteAsync(k);
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

    public void setExpireAsync(RExpirable rObject, long expire, TimeUnit unit) {
        if (expire > 0L) {
            long newExpire = expire;
            if (TimeUnit.MILLISECONDS != unit)
                newExpire = unit.toMillis(expire);
            rObject.expireAsync(Duration.of(newExpire, ChronoUnit.MILLIS));
        }
    }

    public void setExpireIfNotSetAsync(RExpirable rObject, long expire, TimeUnit unit) {
        if (expire > 0L) {
            rObject.isExistsAsync().whenComplete((exists, ex) -> {
                if (!exists)
                    setExpireAsync(rObject, expire, unit);
            });
        }
    }

    /**
     * see #org.redisson.command.CommandAsyncService
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

            throw new RedisException(e.getCause());
        }
    }

    private void removeLocal(String name) {
        CacheElement element = cacheable.getCache(REDIS_LOCAL_KEY);
        if (Objects.isNull(element))
            return;

        Map<String, Class<?>> localKeys =
                (Map<String, Class<?>>) element.getValue();
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
}
