package org.mind.framework.helper;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.util.DateUtils;
import org.mind.framework.util.JarFileUtils;
import org.mind.framework.util.JsonUtils;
import org.redisson.Redisson;
import org.redisson.RedissonShutdownException;
import org.redisson.ScanResult;
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
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
    public static final String LOCK_PREFIX = "LK:";
    public static final String RATE_LIMITED_PREFIX = "RL:";
    public static final String INCREMENT_PREFIX = "ICR:";
    public static final String UNIQUE_ID = "UNIQUE:ID";
    public static final String RESET_ID4DAY = "RESET:ID4DAY";
    public static final String NULL_MARKER = "NULL";
    public static final String EMPTY_LIST_MARKER = "EMPTY_LIST";
    public static final String EMPTY_MAP_MARKER = "EMPTY_MAP";
    public static final String EMPTY_SET_MARKER = "EMPTY_SET";
    public final RedissonClient redissonClient;
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
        return this.<V>rList(name).readAll();
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
        return this.setAsync(name, list, -1, TimeUnit.MILLISECONDS);
    }

    public <V> RFuture<Boolean> setAsync(String name, List<V> list, long expire, TimeUnit unit) {
        if (Objects.isNull(list) || list.isEmpty())
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RList<V> rList = this.rList(name);
        CompletionStage<Boolean> completionStage =
                rList.deleteAsync()
                        .thenCompose(deleted -> rList.addAllAsync(list))
                        .thenCompose(added -> {
                            // 如果 added 为 true 则设置过期，否则返回一个完成的 future
                            CompletionStage<?> expireStage = added
                                    ? setExpireAsync(rList, expire, unit)
                                    : CompletableFuture.completedFuture(null);

                            // 无论是否设置Expire，都会返回true
                            return expireStage.thenApply(v -> Boolean.TRUE);
                        })
                        .exceptionally(ex -> {
                            log.error(ex.getMessage(), ex);
                            return Boolean.FALSE;
                        });

        return new CompletableFutureWrapper<>(completionStage);
    }

    public <V> boolean set(String name, List<V> list) {
        return this.set(name, list, -1, TimeUnit.MILLISECONDS);
    }

    public <V> boolean set(String name, List<V> list, long expire, TimeUnit unit) {
        return this.<Boolean>get(this.setAsync(name, list, expire, unit));
    }

    public <V> boolean set(String name, List<V> list, RLock lock) {
        return this.set(name, list, -1, TimeUnit.MILLISECONDS, lock);
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
        return this.setWithLock(name, list, -1, TimeUnit.MILLISECONDS);
    }

    public <V> boolean setWithLock(String name, List<V> list, long expire, TimeUnit unit) {
        return this.set(name, list, expire, unit, this.getWriteLock(name));
    }

    public <V> RFuture<Boolean> addByListAsync(String name, V v) {
        return this.addByListAsync(name, v, -1, TimeUnit.MILLISECONDS);
    }

    public <V> RFuture<Boolean> addByListAsync(String name, V v, long expire, TimeUnit unit) {
        if (Objects.isNull(v))
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RList<V> rList = this.rList(name);
        return add2Collections(rList.addAsync(v), rList.sizeAsync(), setExpireAsync(rList, expire, unit));
    }

    public <V> boolean addByList(String name, V v) {
        return this.addByList(name, v, -1, TimeUnit.MILLISECONDS);
    }

    public <V> boolean addByList(String name, V v, long expire, TimeUnit unit) {
        return this.<Boolean>get(this.addByListAsync(name, v, expire, unit));
    }

    public <V> boolean addByList(String name, V v, RLock lock) {
        return this.addByList(name, v, -1, TimeUnit.MILLISECONDS, lock);
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
        return this.addByListWithLock(name, v, -1, TimeUnit.MILLISECONDS);
    }

    public <V> boolean addByListWithLock(String name, V v, long expire, TimeUnit unit) {
        return this.addByList(name, v, expire, unit, this.getWriteLock(name));
    }

    public RFuture<Boolean> deleteListAsync(String name) {
        return this.rList(name).deleteAsync();
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
        return this.<K, V>rMap(name).readAllMap();
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
        return this.setAsync(name, map, -1, TimeUnit.MILLISECONDS);
    }

    public <K, V> RFuture<Boolean> setAsync(String name, Map<K, V> map, long expire, TimeUnit unit) {
        if (Objects.isNull(map) || map.isEmpty())
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RMap<K, V> rMap = this.rMap(name);
        CompletionStage<Boolean> completionStage =
                rMap.deleteAsync()
                        .thenCompose(deleted -> rMap.putAllAsync(map))
                        .thenCompose(ignored -> setExpireAsync(rMap, expire, unit).thenApply(expired -> Boolean.TRUE))// 无论是否设置Expire，都会返回true
                        .exceptionally(ex -> {
                            log.error(ex.getMessage(), ex);
                            return Boolean.FALSE;
                        });

        return new CompletableFutureWrapper<>(completionStage);
    }

    public <K, V> boolean set(String name, Map<K, V> map) {
        return this.set(name, map, -1, TimeUnit.MILLISECONDS);
    }

    public <K, V> boolean set(String name, Map<K, V> map, long expire, TimeUnit unit) {
        return this.<Boolean>get(setAsync(name, map, expire, unit));
    }

    public <K, V> boolean set(String name, Map<K, V> map, RLock lock) {
        return this.set(name, map, -1, TimeUnit.MILLISECONDS, lock);
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
        return this.setWithLock(name, map, -1, TimeUnit.MILLISECONDS);
    }

    public <K, V> boolean setWithLock(String name, Map<K, V> map, long expire, TimeUnit unit) {
        return set(name, map, expire, unit, this.getWriteLock(name));
    }

    public <K, V> RFuture<Boolean> putByMapAsync(String name, K k, V v) {
        return this.putByMapAsync(name, k, v, -1, TimeUnit.MILLISECONDS);
    }

    public <K, V> RFuture<Boolean> putByMapAsync(String name, K k, V v, long expire, TimeUnit unit) {
        if (Objects.isNull(k))
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RMap<K, V> rMap = this.rMap(name);
        CompletionStage<Boolean> completionStage =
                rMap.fastPutAsync(k, v).thenCompose(putted -> {
                    if (!putted)
                        return CompletableFuture.completedFuture(Boolean.FALSE);

                    // 通过 size 判断是否首次创建
                    return rMap.sizeAsync().thenCompose(size -> {
                        if (size == 1)
                            return setExpireAsync(rMap, expire, unit).thenApply(expired -> Boolean.TRUE);// 无论是否设置Expire，都会返回true
                        return CompletableFuture.completedFuture(Boolean.TRUE);
                    });
                }).exceptionally(ex -> {
                    log.error(ex.getMessage(), ex);
                    return Boolean.FALSE;
                });

        return new CompletableFutureWrapper<>(completionStage);
    }

    public <K, V> boolean putByMap(String name, K k, V v) {
        return this.putByMap(name, k, v, -1, TimeUnit.MILLISECONDS);
    }

    public <K, V> boolean putByMap(String name, K k, V v, long expire, TimeUnit unit) {
        return this.<Boolean>get(putByMapAsync(name, k, v, expire, unit));
    }

    public <K, V> boolean putByMap(String name, K k, V v, RLock lock) {
        return this.putByMap(name, k, v, -1, TimeUnit.MILLISECONDS, lock);
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
        return this.putByMapWithLock(name, k, v, -1, TimeUnit.MILLISECONDS);
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
        return this.rMap(name).deleteAsync();
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
        return this.<V>rSet(name).readAll();
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
        return this.setAsync(name, set, -1, TimeUnit.MILLISECONDS);
    }

    public <V> RFuture<Boolean> setAsync(String name, Set<V> set, long expire, TimeUnit unit) {
        if (Objects.isNull(set) || set.isEmpty())
            return new CompletableFutureWrapper<>(false);

        RSet<V> rSet = this.rSet(name);
        CompletionStage<Boolean> completionStage =
                rSet.deleteAsync()
                        .thenCompose(deleted -> rSet.addAllAsync(set))
                        .thenCompose(added -> {
                            if (added)
                                return setExpireAsync(rSet, expire, unit).thenApply(expired -> Boolean.TRUE);// 无论是否设置Expire，都会返回true
                            return CompletableFuture.completedFuture(Boolean.TRUE);
                        })
                        .exceptionally(ex -> {
                            log.error(ex.getMessage(), ex);
                            return Boolean.FALSE;
                        });

        return new CompletableFutureWrapper<>(completionStage);
    }

    public <V> boolean set(String name, Set<V> set) {
        return set(name, set, -1, TimeUnit.MILLISECONDS);
    }

    public <V> boolean set(String name, Set<V> set, long expire, TimeUnit unit) {
        return this.<Boolean>get(setAsync(name, set, expire, unit));
    }

    public <V> boolean set(String name, Set<V> set, RLock lock) {
        return set(name, set, -1, TimeUnit.MILLISECONDS, lock);
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
        return setWithLock(name, set, -1, TimeUnit.MILLISECONDS);
    }

    public <V> boolean setWithLock(String name, Set<V> set, long expire, TimeUnit unit) {
        return this.set(name, set, expire, unit, this.getWriteLock(name));
    }

    public <V> RFuture<Boolean> addBySetAsync(String name, V v) {
        return addBySetAsync(name, v, -1, TimeUnit.MILLISECONDS);
    }

    public <V> RFuture<Boolean> addBySetAsync(String name, V v, long expire, TimeUnit unit) {
        if (Objects.isNull(v))
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        RSet<V> rSet = this.rSet(name);
        return add2Collections(rSet.addAsync(v), rSet.sizeAsync(), setExpireAsync(rSet, expire, unit));
    }

    private RFuture<Boolean> add2Collections(RFuture<Boolean> booleanRFuture, RFuture<Integer> integerRFuture, RFuture<Boolean> booleanRFuture2) {
        CompletionStage<Boolean> completionStage =
                booleanRFuture.thenCompose(added -> {
                    if (!added)
                        return CompletableFuture.completedFuture(Boolean.FALSE);

                    // 通过 size 判断是否首次创建
                    return integerRFuture.thenCompose(size -> {
                        if (size == 1)
                            return booleanRFuture2.thenApply(expired -> Boolean.TRUE);// 无论是否设置Expire，都会返回true
                        return CompletableFuture.completedFuture(Boolean.TRUE);
                    });
                }).exceptionally(ex -> {
                    log.error(ex.getMessage(), ex);
                    return Boolean.FALSE;
                });

        return new CompletableFutureWrapper<>(completionStage);
    }

    public <V> boolean addBySet(String name, V v) {
        return addBySet(name, v, -1, TimeUnit.MILLISECONDS);
    }

    public <V> boolean addBySet(String name, V v, long expire, TimeUnit unit) {
        return this.<Boolean>get(addBySetAsync(name, v, expire, unit));
    }

    public <V> boolean addBySet(String name, V v, RLock lock) {
        return addBySet(name, v, -1, TimeUnit.MILLISECONDS, lock);
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
        return addBySetWithLock(name, v, -1, TimeUnit.MILLISECONDS);
    }

    public <V> boolean addBySetWithLock(String name, V v, long expire, TimeUnit unit) {
        return this.addBySet(name, v, expire, unit, this.getWriteLock(name));
    }

    public RFuture<Boolean> deleteSetAsync(String name) {
        return this.rSet(name).deleteAsync();
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
        return this.<V>rBucket(name).get();
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
        return this.rBucket(name).deleteAsync();
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
        return setAsync(name, value, -1, TimeUnit.MILLISECONDS);
    }

    public <V> RFuture<Void> setAsync(String name, V value, long expire, TimeUnit unit) {
        RBucket<V> rBucket = this.rBucket(name);
        return expire > 0L ?
                rBucket.setAsync(value, expire, unit) :
                rBucket.setAsync(value);
    }

    public <V> void set(String name, V value) {
        set(name, value, -1, TimeUnit.MILLISECONDS);
    }

    public <V> void set(String name, V value, long expire, TimeUnit unit) {
        this.get(setAsync(name, value, expire, unit));
    }

    public <V> void setWithLock(String name, V value) {
        setWithLock(name, value, -1, TimeUnit.MILLISECONDS);
    }

    public <V> void setWithLock(String name, V value, long expire, TimeUnit unit) {
        this.set(name, value, expire, unit, this.getWriteLock(name));
    }

    public <V> void set(String name, V value, RLock lock) {
        set(name, value, -1, TimeUnit.MILLISECONDS, lock);
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

    public boolean containsKey(String ... keys){
        return getClient().getKeys().countExists(keys) > 0L;
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

    public boolean setExpire(RExpirable rExpirable, long expire, TimeUnit unit) {
        return this.<Boolean>get(this.setExpireAsync(rExpirable, expire, unit));
    }

    public RFuture<Boolean> setExpireAsync(RExpirable rExpirable, long expire, TimeUnit unit) {
        if (expire <= 0L)
            return new CompletableFutureWrapper<>(Boolean.FALSE);

        long newExpire = expire;
        if (TimeUnit.MILLISECONDS != unit)
            newExpire = unit.toMillis(expire);

        CompletionStage<Boolean> completionStage =
                rExpirable.expireAsync(Duration.ofMillis(newExpire))
                        .thenCompose(success -> {
                            if (success)
                                return CompletableFuture.completedFuture(Boolean.TRUE);
                            return rExpirable.remainTimeToLiveAsync().thenApply(ttl -> ttl > 0L);
                        })
                        .exceptionally(ex -> Boolean.FALSE);

        return new CompletableFutureWrapper<>(completionStage);
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

    /**
     * 利用redis的 HSCAN 分批获取数据
     *
     * @param name    redis key名称
     * @param pattern 如果为null，则获取全部，通配符说明如下:
     *                <p>*：匹配零个或多个字符<p/>
     *                <p>?：匹配一个字符<p/>
     *                <p>[]：匹配方括号内列出的字符范围或字符集。例如 [a-c] 匹配 a、b 或 c<p/>
     *                <p>如: Eg*, 匹配以 "Eg" 开头的键<p/>
     * @param cursor  游标，通常以0开始
     * @param count   扫描数量
     */
    public <R> ScanResult<R> scanBatch(String name, String pattern, long cursor, int count) {
        RedissonClient redissonClient = getClient();
        Codec codec = redissonClient.getConfig().getCodec();
        CommandAsyncExecutor command = ((Redisson) redissonClient).getCommandExecutor();

        RFuture<ScanResult<R>> rf =
                StringUtils.isEmpty(pattern) ?
                        command.readAsync(
                                name, codec, RedisCommands.HSCAN,
                                name, cursor, "COUNT", count)
                        :
                        command.readAsync(name, codec, RedisCommands.HSCAN,
                                name, cursor, "MATCH", pattern, "COUNT", count);
        return command.get(rf);
    }

    public <V> RList<V> rList(String name) {
        return getClient().getList(name);
    }

    public <K, V> RMap<K, V> rMap(String name) {
        return getClient().getMap(name);
    }

    public <V> RSet<V> rSet(String name) {
        return getClient().getSet(name);
    }

    public <V> RBucket<V> rBucket(String name) {
        return getClient().getBucket(name);
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
        if (Objects.isNull(duration)) {
            log.error("Failed to calculate the duration from midnight.");
            return;
        }

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
