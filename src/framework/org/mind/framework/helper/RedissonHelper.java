package org.mind.framework.helper;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.cache.CacheElement;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.cache.LruCache;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.service.ExecutorFactory;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.util.DateFormatUtils;
import org.mind.framework.util.JarFileUtils;
import org.redisson.Redisson;
import org.redisson.api.LockOptions;
import org.redisson.api.RBucket;
import org.redisson.api.RIdGenerator;
import org.redisson.api.RList;
import org.redisson.api.RLock;
import org.redisson.api.RLongAdder;
import org.redisson.api.RMap;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RSet;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.mind.framework.server.WebServerConfig.JAR_IN_CLASSES;

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
            log.error(e.getMessage(), e);
            ThrowProvider.doThrow(e);
        }

        // Used to store the key added to redis
        cacheable = LruCache.initCache();
        redissonClient = Redisson.create(config);
        Runtime.getRuntime().addShutdownHook(ExecutorFactory.newThread("Redisson-Gracefully", true, () -> {
            if (!redissonClient.isShutdown()) {
                log.info("Redisson-Gracefully is shutdown ....");
                redissonClient.shutdown(3L, 5L, TimeUnit.SECONDS);// timeout should >= quietPeriod
            }
        }));
    }

    public static RedissonHelper getInstance() {
        return Helper.INSTANCE;
    }

    public static RedissonClient getClient() {
        return getInstance().redissonClient;
    }

    public <V> List<V> getList(String key) {
        RList<V> rList = this.rList(key);
        if (rList.isEmpty())
            return Collections.emptyList();

        List<V> list = new ArrayList<>(rList.size());
        list.addAll(rList);
        return list;
    }

    public <V> List<V> getList(String key, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.getList(key);
        } finally {
            lock.unlock();
        }
    }

    public <V> List<V> getListByLock(String key) {
        return this.getList(key, getReadLock(key));
    }

    public <V> void setAsync(String key, List<V> list, long expire, TimeUnit unit) {
        if (Objects.isNull(list) || list.isEmpty())
            return;

        RList<V> rList = this.rList(key);
        if (!rList.isEmpty())
            rList.clear();

        rList.addAllAsync(list);
        if (expire > 0L) {
            if (TimeUnit.MILLISECONDS != unit)
                expire = unit.toMillis(expire);
            rList.expireAsync(Duration.of(expire, ChronoUnit.MILLIS));
        }
        this.putLocal(key, list.getClass());
    }

    public <V> boolean set(String key, List<V> list, long expire, TimeUnit unit) {
        if (Objects.isNull(list) || list.isEmpty())
            return false;

        RList<V> rList = this.rList(key);
        if (!rList.isEmpty())
            rList.clear();

        boolean flag = rList.addAll(list);
        if (expire > 0L) {
            if (TimeUnit.MILLISECONDS != unit)
                expire = unit.toMillis(expire);
            rList.expireAsync(Duration.of(expire, ChronoUnit.MILLIS));
        }
        this.putLocal(key, list.getClass());
        return flag;
    }

    public <V> boolean set(String key, List<V> list, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.set(key, list, expire, unit);
        } finally {
            lock.unlock();
        }
    }

    public <V> boolean setByLock(String key, List<V> list, long expire, TimeUnit unit) {
        return this.set(key, list, expire, unit, this.getWriteLock(key));
    }

    public <V> boolean appendList(String key, V v, long expire, TimeUnit unit) {
        if (Objects.isNull(v))
            return false;

        RList<V> rList = this.rList(key);
        if (rList.isExists())
            return rList.add(v);

        boolean flag = rList.add(v);
        if(expire > 0L) {
            if (TimeUnit.MILLISECONDS != unit)
                expire = unit.toMillis(expire);
            rList.expireAsync(Duration.of(expire, ChronoUnit.MILLIS));
        }
        this.putLocal(key, ArrayList.class);
        return flag;
    }

    public <V> boolean appendList(String key, V v, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.appendList(key, v, expire, unit);
        } finally {
            lock.unlock();
        }
    }

    public <V> boolean appendListByLock(String key, V v, long expire, TimeUnit unit) {
        return this.appendList(key, v, expire, unit, this.getWriteLock(key));
    }

    public <V> void clearListAsync(String key) {
        RList<V> rList = this.rList(key);
        if (rList.isEmpty())
            return;

        rList.deleteAsync();
        this.removeLocal(key);
    }

    public <V> void clearList(String key) {
        RList<V> rList = this.rList(key);
        if (rList.isEmpty())
            return;

        rList.clear();
        this.removeLocal(key);
    }

    public <V> void clearList(String key, RLock lock) {
        lock.lock();
        try {
            this.<V>clearList(key);
        } finally {
            lock.unlock();
        }
    }

    public <V> void clearListByLock(String key) {
        this.<V>clearList(key, this.getWriteLock(key));
    }

    public <V> void removeListAsync(String key, int index) {
        if (index < 0)
            return;

        RList<V> rList = this.rList(key);
        if (rList.size() > index)
            rList.removeAsync(index);
    }

    public <V> void removeList(String key, int index) {
        if (index < 0)
            return;

        RList<V> rList = this.rList(key);
        if (rList.size() > index)
            rList.remove(index);
    }

    public <V> void removeList(String key, int index, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            this.removeList(key, index);
        } finally {
            lock.unlock();
        }
    }

    public <V> void removeListByLock(String key, int index) {
        if (index < 0)
            return;

        this.removeList(key, index, this.getWriteLock(key));
    }

    public <K, V> Map<K, V> getMap(String key) {
        RMap<K, V> rMap = this.rMap(key);
        if (rMap.isEmpty())
            return Collections.EMPTY_MAP;

        Map<K, V> map = new HashMap<>(rMap.size());
        map.putAll(rMap);
        return map;
    }

    public <K, V> Map<K, V> getMap(String key, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.getMap(key);
        } finally {
            lock.unlock();
        }
    }

    public <K, V> Map<K, V> getMapByLock(String key) {
        return this.getMap(key, getReadLock(key));
    }

    public <K, V> void setAsync(String key, Map<K, V> map, long expire, TimeUnit unit) {
        if (Objects.isNull(map) || map.isEmpty())
            return;

        RMap<K, V> rMap = this.rMap(key);
        if (!rMap.isEmpty())
            rMap.clear();

        rMap.putAllAsync(map);
        if (expire > 0L) {
            if (TimeUnit.MILLISECONDS != unit)
                expire = unit.toMillis(expire);
            rMap.expireAsync(Duration.of(expire, ChronoUnit.MILLIS));
        }
        this.putLocal(key, map.getClass());
    }

    public <K, V> boolean set(String key, Map<K, V> map, long expire, TimeUnit unit) {
        if (Objects.isNull(map) || map.isEmpty())
            return false;

        RMap<K, V> rMap = this.rMap(key);
        if (!rMap.isEmpty())
            rMap.clear();

        rMap.putAll(map);
        if (expire > 0L) {
            if (TimeUnit.MILLISECONDS != unit)
                expire = unit.toMillis(expire);
            rMap.expireAsync(Duration.of(expire, ChronoUnit.MILLIS));
        }
        this.putLocal(key, map.getClass());
        return true;
    }

    public <K, V> boolean set(String key, Map<K, V> map, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.set(key, map, expire, unit);
        } finally {
            lock.unlock();
        }
    }

    public <K, V> boolean setByLock(String key, Map<K, V> map, long expire, TimeUnit unit) {
        return set(key, map, expire, unit, this.getWriteLock(key));
    }

    public <K, V> boolean appendMap(String key, K k, V v, long expire, TimeUnit unit) {
        if (Objects.isNull(k))
            return false;

        RMap<K, V> rMap = this.rMap(key);
        if (rMap.isExists())
            return rMap.fastPut(k, v);

        boolean flag = rMap.fastPut(k, v);
        if(expire > 0L) {
            if (TimeUnit.MILLISECONDS != unit)
                expire = unit.toMillis(expire);
            rMap.expireAsync(Duration.of(expire, ChronoUnit.MILLIS));
        }
        this.putLocal(key, HashMap.class);
        return flag;
    }

    public <K, V> boolean appendMap(String key, K k, V v, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.appendMap(key, k, v, expire, unit);
        } finally {
            lock.unlock();
        }
    }

    public <K, V> boolean appendMapByLock(String key, K k, V v, long expire, TimeUnit unit) {
        return this.appendMap(key, k, v, expire, unit, this.getWriteLock(key));
    }

    public <K, V> void replaceMap(String key, K k, V v) {
        if (Objects.isNull(k))
            return;

        RMap<K, V> rMap = this.rMap(key);
        if (rMap.isEmpty())
            return;

        if (rMap.containsKey(k))
            rMap.replace(k, v);
    }

    public <K, V> void replaceMap(String key, K k, V v, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            this.replaceMap(key, k, v);
        } finally {
            lock.unlock();
        }
    }

    public <K, V> void replaceMapByLock(String key, K k, V v) {
        if (Objects.isNull(k))
            return;

        this.replaceMap(key, k, v, this.getWriteLock(key));
    }

    public <K, V> void clearMapAsync(String key) {
        RMap<K, V> rMap = this.rMap(key);
        if (rMap.isEmpty())
            return;

        rMap.deleteAsync();
        this.removeLocal(key);
    }

    public <K, V> void clearMap(String key) {
        RMap<K, V> rMap = this.rMap(key);
        if (rMap.isEmpty())
            return;

        rMap.clear();
        this.removeLocal(key);
    }

    public <K, V> void clearMap(String key, RLock lock) {
        lock.lock();
        try {
            this.<K, V>clearMap(key);
        } finally {
            lock.unlock();
        }
    }

    public <K, V> void clearMapByLock(String key) {
        this.<K, V>clearMap(key, this.getWriteLock(key));
    }

    public <K, V> void removeMapAsync(String key, K k) {
        if (Objects.isNull(k))
            return;

        RMap<K, V> rMap = this.rMap(key);
        if (rMap.isEmpty())
            return;

        if (rMap.containsKey(k))
            rMap.removeAsync(k);
    }


    public <K, V> void removeMap(String key, K k) {
        if (Objects.isNull(k))
            return;

        RMap<K, V> rMap = this.rMap(key);
        if (rMap.isEmpty())
            return;

        rMap.remove(k);
    }

    public <K, V> void removeMap(String key, K k, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            this.removeMap(key, k);
        } finally {
            lock.unlock();
        }
    }

    public <K, V> void removeMapByLock(String key, K k) {
        if (Objects.isNull(k))
            return;

        this.removeMap(key, k, this.getWriteLock(key));
    }

    public <V> Set<V> getSet(String key) {
        RSet<V> rSet = this.rSet(key);
        if (rSet.isEmpty())
            return Collections.emptySet();

        Set<V> set = new HashSet<>(rSet.size());
        set.addAll(rSet);
        return set;
    }

    public <V> Set<V> getSet(String key, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.getSet(key);
        } finally {
            lock.unlock();
        }
    }

    public <V> Set<V> getSetByLock(String key) {
        return this.getSet(key, getReadLock(key));
    }

    public <V> void setAsync(String key, Set<V> set, long expire, TimeUnit unit) {
        if (Objects.isNull(set) || set.isEmpty())
            return;

        RSet<V> rSet = this.rSet(key);
        if (!rSet.isEmpty())
            rSet.clear();

        rSet.addAllAsync(set);
        if (expire > 0L) {
            if (TimeUnit.MILLISECONDS != unit)
                expire = unit.toMillis(expire);
            rSet.expireAsync(Duration.of(expire, ChronoUnit.MILLIS));
        }
        this.putLocal(key, set.getClass());
    }

    public <V> boolean set(String key, Set<V> set, long expire, TimeUnit unit) {
        if (Objects.isNull(set) || set.isEmpty())
            return false;

        RSet<V> rSet = this.rSet(key);
        if (!rSet.isEmpty())
            rSet.clear();

        boolean flag = rSet.addAll(set);
        if (expire > 0L) {
            if (TimeUnit.MILLISECONDS != unit)
                expire = unit.toMillis(expire);
            rSet.expireAsync(Duration.of(expire, ChronoUnit.MILLIS));
        }
        this.putLocal(key, set.getClass());
        return flag;
    }

    public <V> boolean set(String key, Set<V> set, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.set(key, set, expire, unit);
        } finally {
            lock.unlock();
        }
    }

    public <V> boolean setByLock(String key, Set<V> set, long expire, TimeUnit unit) {
        return this.set(key, set, expire, unit, this.getWriteLock(key));
    }

    public <V> boolean appendSet(String key, V v, long expire, TimeUnit unit) {
        if (Objects.isNull(v))
            return false;

        RSet<V> rSet = this.rSet(key);
        if (rSet.isExists())
            return rSet.add(v);

        boolean flag = rSet.add(v);
        if (expire > 0L) {
            if (TimeUnit.MILLISECONDS != unit)
                expire = unit.toMillis(expire);
            rSet.expireAsync(Duration.of(expire, ChronoUnit.MILLIS));
        }
        this.putLocal(key, HashSet.class);
        return flag;
    }

    public <V> boolean appendSet(String key, V v, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.appendSet(key, v, expire, unit);
        } finally {
            lock.unlock();
        }
    }

    public <V> boolean appendSetByLock(String key, V v, long expire, TimeUnit unit) {
        return this.appendSet(key, v, expire, unit, this.getWriteLock(key));
    }

    public <V> void clearSetAsync(String key) {
        RSet<V> rSet = this.rSet(key);
        if (rSet.isEmpty())
            return;

        rSet.deleteAsync();
        this.removeLocal(key);
    }

    public <V> void clearSet(String key) {
        RSet<V> rSet = this.rSet(key);
        if (rSet.isEmpty())
            return;

        rSet.clear();
        this.removeLocal(key);
    }

    public <V> void clearSet(String key, RLock lock) {
        lock.lock();
        try {
            this.<V>clearSet(key);
        } finally {
            lock.unlock();
        }
    }

    public <V> void clearSetByLock(String key) {
        this.<V>clearSet(key, this.getWriteLock(key));
    }

    public <V> void removeSetAsync(String key, int index) {
        if (index < 0)
            return;

        RSet<V> rSet = this.rSet(key);
        if (rSet.size() > index)
            rSet.removeAsync(index);
    }

    public <V> void removeSet(String key, V v) {
        RSet<V> rSet = this.rSet(key);
        if (!rSet.isEmpty())
            rSet.remove(v);
    }

    public <V> void removeSet(String key, V v, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            this.removeSet(key, v);
        } finally {
            lock.unlock();
        }
    }

    public <V> void removeSetByLock(String key, V v) {
        this.removeSet(key, v, this.getWriteLock(key));
    }

    public <V> V get(String key) {
        return (V) getClient().getBucket(key).get();
    }

    public <V> V getByLock(String key) {
        return this.get(key, this.getReadLock(key));
    }

    public <V> V get(String key, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.get(key);
        } finally {
            lock.unlock();
        }
    }

    public void removeAsync(String key) {
        getClient().getBucket(key).deleteAsync();
        this.removeLocal(key);
    }

    public boolean remove(String key) {
        boolean flag = getClient().getBucket(key).delete();
        this.removeLocal(key);
        return flag;
    }

    public boolean remove(String key, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.remove(key);
        } finally {
            lock.unlock();
        }
    }

    public boolean removeByLock(String key) {
        return this.remove(key, this.getWriteLock(key));
    }

    public <V> void setAsync(String key, V value, long expire, TimeUnit unit) {
        RBucket<V> rBucket = getClient().getBucket(key);
        if (expire > 0)
            rBucket.setAsync(value, expire, unit);
        else
            rBucket.setAsync(value);
        this.putLocal(key, value.getClass());
    }

    public <V> void set(String key, V value, long expire, TimeUnit unit) {
        RBucket<V> rBucket = getClient().getBucket(key);
        if (expire > 0)
            rBucket.set(value, expire, unit);
        else
            rBucket.set(value);
        this.putLocal(key, value.getClass());
    }

    public <V> void setByLock(String key, V value, long expire, TimeUnit unit) {
        this.set(key, value, expire, unit, this.getWriteLock(key));
    }

    public <V> void set(String key, V value, long expire, TimeUnit unit, RLock Lock) {
        // activating watch-dog
        Lock.lock();
        try {
            this.set(key, value, expire, unit);
        } finally {
            Lock.unlock();
        }
    }

    public long getIdForDate() {
        String date = DateFormatUtils.dateNow().format(DateTimeFormatter.ofPattern("yyMMdd"));
        return Long.parseLong(String.format("%s%d", date, getId()));
    }

    public long getId() {
        return getId(100_000L, 1_000L);
    }

    public long getId(long start, long allocationSize) {
        RIdGenerator idGenerator = getClient().getIdGenerator(UNIQUE_ID);
        idGenerator.tryInit(start, allocationSize);
        return idGenerator.nextId();
    }

    public void increment(String name) {
        this.increment(name, 1L);
    }

    public void increment(String name, long add) {
        RLongAdder adder = getClient().getLongAdder(INCREMENT_PREFIX + name);
        adder.add(add);
    }

    public void decrement(String name) {
        this.decrement(name, 1L);
    }

    public void decrement(String name, long value) {
        RLongAdder adder = getClient().getLongAdder(INCREMENT_PREFIX + name);
        adder.add(-value);
    }

    public void reset(String name) {
        RLongAdder adder = getClient().getLongAdder(INCREMENT_PREFIX + name);
        adder.reset();
    }

    public long sum(String name) {
        RLongAdder adder = getClient().getLongAdder(INCREMENT_PREFIX + name);
        return adder.sum();
    }

    public RRateLimiter getRateLimiter(String name, long rate, long interval, TimeUnit unit) {
        RRateLimiter rl = getClient().getRateLimiter(RATE_LIMITED_PREFIX + name);
        if (rl.isExists())
            return rl;

        if (TimeUnit.SECONDS != unit)
            interval = unit.toSeconds(interval);

        rl.trySetRate(RateType.OVERALL, rate, interval, RateIntervalUnit.SECONDS);
        return rl;
    }

    public RLock getLock(String key) {
        return getClient().getLock(LOCK_PREFIX + key);
    }

    public RReadWriteLock getReadWriteLock(String key) {
        return getClient().getReadWriteLock(LOCK_PREFIX + key);
    }

    public RLock getReadLock(String key) {
        return this.getReadWriteLock(key).readLock();
    }

    public RLock getWriteLock(String key) {
        return this.getReadWriteLock(key).writeLock();
    }

    public RLock getFairLock(String key) {
        return getClient().getFairLock(LOCK_PREFIX + key);
    }

    public RLock getSpinLock(String key) {
        return getClient().getSpinLock(LOCK_PREFIX + key);
    }

    public RLock getSpinLock(String key, LockOptions.BackOff backOff) {
        return getClient().getSpinLock(LOCK_PREFIX + key, backOff);
    }

    public void removeContainsFromLocalKeys(String keyPart) {
        CacheElement element = cacheable.getCache(REDIS_LOCAL_KEY);
        if (Objects.isNull(element))
            return;

        Map<String, Class<?>> redisKeys =
                (Map<String, Class<?>>) element.getValue();
        if (Objects.isNull(redisKeys) || redisKeys.isEmpty())
            return;

        Iterator<Map.Entry<String, Class<?>>> iterator = redisKeys.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Class<?>> entry = iterator.next();
            if (StringUtils.contains(entry.getKey(), keyPart)) {
                if (List.class.isAssignableFrom(entry.getValue()))
                    this.clearListAsync(entry.getKey());
                else if (Map.class.isAssignableFrom(entry.getValue()))
                    this.clearMapAsync(entry.getKey());
                else if (Set.class.isAssignableFrom(entry.getValue()))
                    this.clearSetAsync(entry.getKey());
                else
                    this.removeAsync(entry.getKey());
            }
        }
    }

    public List<String> getLocalKeys(Class<?> clazzType) {
        CacheElement element = cacheable.getCache(REDIS_LOCAL_KEY);

        if (Objects.isNull(element))
            return Collections.EMPTY_LIST;

        Map<String, Class<?>> redisKeys =
                (Map<String, Class<?>>) element.getValue();

        if (Objects.isNull(redisKeys) || redisKeys.isEmpty())
            return Collections.EMPTY_LIST;

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
            return Collections.EMPTY_MAP;

        Map<String, Class<?>> redisKeys =
                (Map<String, Class<?>>) element.getValue();

        if (Objects.isNull(redisKeys) || redisKeys.isEmpty())
            return Collections.EMPTY_MAP;

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
            return Collections.EMPTY_MAP;

        Map<String, Class<?>> keysMap =
                (Map<String, Class<?>>) element.getValue();

        if (Objects.isNull(keysMap) || keysMap.isEmpty())
            return Collections.EMPTY_MAP;

        Map<String, Class<?>> resultMap = new HashMap<>(keysMap.size());
        resultMap.putAll(keysMap);
        return resultMap;
    }

    private void removeLocal(String key) {
        CacheElement element = cacheable.getCache(REDIS_LOCAL_KEY);
        if (Objects.isNull(element))
            return;

        Map<String, Class<?>> localKeys =
                (Map<String, Class<?>>) element.getValue();
        if (Objects.isNull(localKeys) || localKeys.isEmpty())
            return;

        localKeys.remove(key);
    }

    private void putLocal(String key, Class<?> clazzType) {
        CacheElement element = cacheable.getCache(REDIS_LOCAL_KEY);
        if (Objects.isNull(element)) {
            Map<String, Class<?>> redisKeys = new ConcurrentHashMap<>();
            redisKeys.put(key, clazzType);
            cacheable.addCache(REDIS_LOCAL_KEY, redisKeys, true);
            return;
        }

        Map<String, Class<?>> redisKeys =
                (Map<String, Class<?>>) element.getValue();
        redisKeys.putIfAbsent(key, clazzType);
    }

    private <V> RList<V> rList(String key) {
        return getClient().getList(key);
    }

    private <K, V> RMap<K, V> rMap(String key) {
        return getClient().getMap(key);
    }

    private <V> RSet<V> rSet(String key) {
        return getClient().getSet(key);
    }
}