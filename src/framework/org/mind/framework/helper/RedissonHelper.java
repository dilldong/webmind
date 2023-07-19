package org.mind.framework.helper;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.cache.CacheElement;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.cache.LruCache;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.util.DateFormatUtils;
import org.mind.framework.util.JarFileUtils;
import org.redisson.Redisson;
import org.redisson.RedissonShutdownException;
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
import java.time.ZoneId;
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
import java.util.concurrent.Future;
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
            ThrowProvider.doThrow(e);
        }

        // Used to store the key added to redis
        cacheable = LruCache.initCache();
        redissonClient = Redisson.create(config);
        Runtime.getRuntime().addShutdownHook(ExecutorFactory.newThread("Redisson-Gracefully", true, () -> {
            if (!redissonClient.isShutdown()) {
                log.info("Redisson-Gracefully is shutdown ....");
                try {
                    redissonClient.shutdown(10L, 15L, TimeUnit.SECONDS);// timeout should >= quietPeriod
                }catch (RedissonShutdownException e){
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

    public <V> List<V> getList(String name) {
        RList<V> rList = this.rList(name);
        if (rList.isEmpty())
            return Collections.emptyList();

        List<V> list = new ArrayList<>(rList.size());
        list.addAll(rList);
        return list;
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

    public <V> void setAsync(String name, List<V> list, long expire, TimeUnit unit) {
        if (Objects.isNull(list) || list.isEmpty())
            return;

        RList<V> rList = this.rList(name);
        if (!rList.isEmpty())
            rList.clear();

        rList.addAllAsync(list);
        if (expire > 0L) {
            long newExpire = expire;
            if (TimeUnit.MILLISECONDS != unit)
                newExpire = unit.toMillis(expire);
            rList.expireAsync(Duration.of(newExpire, ChronoUnit.MILLIS));
        }
        this.putLocal(name, list.getClass());
    }

    public <V> boolean set(String name, List<V> list, long expire, TimeUnit unit) {
        if (Objects.isNull(list) || list.isEmpty())
            return false;

        RList<V> rList = this.rList(name);
        if (!rList.isEmpty())
            rList.clear();

        boolean flag = rList.addAll(list);
        if (expire > 0L) {
            long newExpire = expire;
            if (TimeUnit.MILLISECONDS != unit)
                newExpire = unit.toMillis(expire);
            rList.expireAsync(Duration.of(newExpire, ChronoUnit.MILLIS));
        }
        this.putLocal(name, list.getClass());
        return flag;
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

    public <V> boolean appendList(String name, V v, long expire, TimeUnit unit) {
        if (Objects.isNull(v))
            return false;

        RList<V> rList = this.rList(name);
        if (rList.isExists())
            return rList.add(v);

        rList.add(v);
        if (expire > 0L) {
            long newExpire = expire;
            if (TimeUnit.MILLISECONDS != unit)
                newExpire = unit.toMillis(expire);
            rList.expireAsync(Duration.of(newExpire, ChronoUnit.MILLIS));
        }
        this.putLocal(name, ArrayList.class);
        return true;
    }

    public <V> boolean appendList(String name, V v, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.appendList(name, v, expire, unit);
        } finally {
            lock.unlock();
        }
    }

    public <V> boolean appendListWithLock(String name, V v, long expire, TimeUnit unit) {
        return this.appendList(name, v, expire, unit, this.getWriteLock(name));
    }

    public <V> void clearListAsync(String name) {
        RList<V> rList = this.rList(name);
        if (rList.isEmpty())
            return;

        rList.deleteAsync();
        this.removeLocal(name);
    }

    public <V> void clearList(String name) {
        RList<V> rList = this.rList(name);
        if (rList.isEmpty())
            return;

        rList.clear();
        this.removeLocal(name);
    }

    public <V> void clearList(String name, RLock lock) {
        lock.lock();
        try {
            this.<V>clearList(name);
        } finally {
            lock.unlock();
        }
    }

    public <V> void clearListWithLock(String name) {
        this.<V>clearList(name, this.getWriteLock(name));
    }

    public <V> void removeListAsync(String name, int index) {
        if (index < 0)
            return;

        RList<V> rList = this.rList(name);
        if (rList.size() > index)
            rList.removeAsync(index);
    }

    public <V> void removeList(String name, int index) {
        if (index < 0)
            return;

        RList<V> rList = this.rList(name);
        if (rList.size() > index)
            rList.remove(index);
    }

    public <V> void removeList(String name, int index, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            this.removeList(name, index);
        } finally {
            lock.unlock();
        }
    }

    public <V> void removeListWithLock(String name, int index) {
        if (index < 0)
            return;

        this.removeList(name, index, this.getWriteLock(name));
    }

    public <K, V> V getMapValue(String name, K k) {
        RMap<K, V> rMap = this.rMap(name);
        if (rMap.isEmpty())
            return null;

        return rMap.get(k);
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
        RMap<K, V> rMap = this.rMap(name);
        if (rMap.isEmpty())
            return Collections.emptyMap();

        Map<K, V> map = new HashMap<>(rMap.size());
        map.putAll(rMap);
        return map;
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

    public <K, V> void setAsync(String name, Map<K, V> map, long expire, TimeUnit unit) {
        if (Objects.isNull(map) || map.isEmpty())
            return;

        RMap<K, V> rMap = this.rMap(name);
        if (!rMap.isEmpty())
            rMap.clear();

        rMap.putAllAsync(map);
        if (expire > 0L) {
            long newExpire = expire;
            if (TimeUnit.MILLISECONDS != unit)
                newExpire = unit.toMillis(expire);
            rMap.expireAsync(Duration.of(newExpire, ChronoUnit.MILLIS));
        }
        this.putLocal(name, map.getClass());
    }

    public <K, V> boolean set(String name, Map<K, V> map, long expire, TimeUnit unit) {
        if (Objects.isNull(map) || map.isEmpty())
            return false;

        RMap<K, V> rMap = this.rMap(name);
        if (!rMap.isEmpty())
            rMap.clear();

        rMap.putAll(map);
        if (expire > 0L) {
            long newExpire = expire;
            if (TimeUnit.MILLISECONDS != unit)
                newExpire = unit.toMillis(expire);
            rMap.expireAsync(Duration.of(newExpire, ChronoUnit.MILLIS));
        }
        this.putLocal(name, map.getClass());
        return true;
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

    public <K, V> boolean appendMap(String name, K k, V v, long expire, TimeUnit unit) {
        if (Objects.isNull(k))
            return false;

        RMap<K, V> rMap = this.rMap(name);
        if (rMap.isExists())
            return rMap.fastPut(k, v);

        boolean flag = rMap.fastPut(k, v);
        if (expire > 0L) {
            long newExpire = expire;
            if (TimeUnit.MILLISECONDS != unit)
                newExpire = unit.toMillis(expire);
            rMap.expireAsync(Duration.of(newExpire, ChronoUnit.MILLIS));
        }
        this.putLocal(name, HashMap.class);
        return flag;
    }

    public <K, V> boolean appendMap(String name, K k, V v, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.appendMap(name, k, v, expire, unit);
        } finally {
            lock.unlock();
        }
    }

    public <K, V> boolean appendMapWithLock(String name, K k, V v, long expire, TimeUnit unit) {
        return this.appendMap(name, k, v, expire, unit, this.getWriteLock(name));
    }

    public <K, V> void replaceMap(String name, K k, V v) {
        if (Objects.isNull(k))
            return;

        RMap<K, V> rMap = this.rMap(name);
        if (rMap.isEmpty())
            return;

        if (rMap.containsKey(k))
            rMap.replace(k, v);
    }

    public <K, V> void replaceMap(String name, K k, V v, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            this.replaceMap(name, k, v);
        } finally {
            lock.unlock();
        }
    }

    public <K, V> void replaceMapWithLock(String name, K k, V v) {
        if (Objects.isNull(k))
            return;

        this.replaceMap(name, k, v, this.getWriteLock(name));
    }

    public <K, V> void clearMapAsync(String name) {
        RMap<K, V> rMap = this.rMap(name);
        if (rMap.isEmpty())
            return;

        rMap.deleteAsync();
        this.removeLocal(name);
    }

    public <K, V> void clearMap(String name) {
        RMap<K, V> rMap = this.rMap(name);
        if (rMap.isEmpty())
            return;

        rMap.clear();
        this.removeLocal(name);
    }

    public <K, V> void clearMap(String name, RLock lock) {
        lock.lock();
        try {
            this.<K, V>clearMap(name);
        } finally {
            lock.unlock();
        }
    }

    public <K, V> void clearMapWithLock(String name) {
        this.<K, V>clearMap(name, this.getWriteLock(name));
    }

    public <K, V> void removeMapAsync(String name, K k) {
        if (Objects.isNull(k))
            return;

        RMap<K, V> rMap = this.rMap(name);
        if (rMap.isEmpty())
            return;

        if (rMap.containsKey(k))
            rMap.removeAsync(k);
    }


    public <K, V> void removeMap(String name, K k) {
        if (Objects.isNull(k))
            return;

        RMap<K, V> rMap = this.rMap(name);
        if (rMap.isEmpty())
            return;

        rMap.remove(k);
    }

    public <K, V> void removeMap(String name, K k, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            this.<K, V>removeMap(name, k);
        } finally {
            lock.unlock();
        }
    }

    public <K, V> void removeMapWithLock(String name, K k) {
        if (Objects.isNull(k))
            return;

        this.<K, V>removeMap(name, k, this.getWriteLock(name));
    }

    public <V> Set<V> getSet(String name) {
        RSet<V> rSet = this.rSet(name);
        if (rSet.isEmpty())
            return Collections.emptySet();

        Set<V> set = new HashSet<>(rSet.size());
        set.addAll(rSet);
        return set;
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

    public <V> void setAsync(String name, Set<V> set, long expire, TimeUnit unit) {
        if (Objects.isNull(set) || set.isEmpty())
            return;

        RSet<V> rSet = this.rSet(name);
        if (!rSet.isEmpty())
            rSet.clear();

        rSet.addAllAsync(set);
        if (expire > 0L) {
            long newExpire = expire;
            if (TimeUnit.MILLISECONDS != unit)
                newExpire = unit.toMillis(expire);
            rSet.expireAsync(Duration.of(newExpire, ChronoUnit.MILLIS));
        }
        this.putLocal(name, set.getClass());
    }

    public <V> boolean set(String name, Set<V> set, long expire, TimeUnit unit) {
        if (Objects.isNull(set) || set.isEmpty())
            return false;

        RSet<V> rSet = this.rSet(name);
        if (!rSet.isEmpty())
            rSet.clear();

        boolean flag = rSet.addAll(set);
        if (expire > 0L) {
            long newExpire = expire;
            if (TimeUnit.MILLISECONDS != unit)
                newExpire = unit.toMillis(expire);
            rSet.expireAsync(Duration.of(newExpire, ChronoUnit.MILLIS));
        }
        this.putLocal(name, set.getClass());
        return flag;
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

    public <V> boolean appendSet(String name, V v, long expire, TimeUnit unit) {
        if (Objects.isNull(v))
            return false;

        RSet<V> rSet = this.rSet(name);
        if (rSet.isExists())
            return rSet.add(v);

        boolean flag = rSet.add(v);
        if (expire > 0L) {
            long newExpire = expire;
            if (TimeUnit.MILLISECONDS != unit)
                newExpire = unit.toMillis(expire);
            rSet.expireAsync(Duration.of(newExpire, ChronoUnit.MILLIS));
        }
        this.putLocal(name, HashSet.class);
        return flag;
    }

    public <V> boolean appendSet(String name, V v, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.appendSet(name, v, expire, unit);
        } finally {
            lock.unlock();
        }
    }

    public <V> boolean appendSetWithLock(String name, V v, long expire, TimeUnit unit) {
        return this.appendSet(name, v, expire, unit, this.getWriteLock(name));
    }

    public <V> void clearSetAsync(String name) {
        RSet<V> rSet = this.rSet(name);
        if (rSet.isEmpty())
            return;

        rSet.deleteAsync();
        this.removeLocal(name);
    }

    public <V> void clearSet(String name) {
        RSet<V> rSet = this.rSet(name);
        if (rSet.isEmpty())
            return;

        rSet.clear();
        this.removeLocal(name);
    }

    public <V> void clearSet(String name, RLock lock) {
        lock.lock();
        try {
            this.<V>clearSet(name);
        } finally {
            lock.unlock();
        }
    }

    public <V> void clearSetWithLock(String name) {
        this.<V>clearSet(name, this.getWriteLock(name));
    }

    public <V> void removeSetAsync(String name, int index) {
        if (index < 0)
            return;

        RSet<V> rSet = this.rSet(name);
        if (rSet.size() > index)
            rSet.removeAsync(index);
    }

    public <V> void removeSet(String name, V v) {
        RSet<V> rSet = this.rSet(name);
        if (!rSet.isEmpty())
            rSet.remove(v);
    }

    public <V> void removeSet(String name, V v, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            this.removeSet(name, v);
        } finally {
            lock.unlock();
        }
    }

    public <V> void removeSetWithLock(String name, V v) {
        this.removeSet(name, v, this.getWriteLock(name));
    }

    public <V> V get(String name) {
        return (V) getClient().getBucket(name).get();
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

    public void removeAsync(String name) {
        getClient().getBucket(name).deleteAsync();
        this.removeLocal(name);
    }

    public boolean remove(String name) {
        boolean flag = getClient().getBucket(name).delete();
        this.removeLocal(name);
        return flag;
    }

    public boolean remove(String name, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            return this.remove(name);
        } finally {
            lock.unlock();
        }
    }

    public boolean removeWithLock(String name) {
        return this.remove(name, this.getWriteLock(name));
    }

    public <V> void setAsync(String name, V value, long expire, TimeUnit unit) {
        RBucket<V> rBucket = getClient().getBucket(name);
        if (expire > 0)
            rBucket.setAsync(value, expire, unit);
        else
            rBucket.setAsync(value);
        this.putLocal(name, value.getClass());
    }

    public <V> void set(String name, V value, long expire, TimeUnit unit) {
        RBucket<V> rBucket = getClient().getBucket(name);
        if (expire > 0)
            rBucket.set(value, expire, unit);
        else
            rBucket.set(value);
        this.putLocal(name, value.getClass());
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
        return getIdForDate(zone, "yyMMdd");
    }

    public long getIdForDate(ZoneId zone, String dateFormat) {
        String date = DateFormatUtils.dateNow(zone).format(DateTimeFormatter.ofPattern(dateFormat));
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

    public void resetAsync(String name) {
        RLongAdder adder = getClient().getLongAdder(INCREMENT_PREFIX + name);
        adder.resetAsync();
    }

    public long sum(String name) {
        RLongAdder adder = getClient().getLongAdder(INCREMENT_PREFIX + name);
        return adder.sum();
    }

    public Future<Long> sumAsync(String name) {
        RLongAdder adder = getClient().getLongAdder(INCREMENT_PREFIX + name);
        return adder.sumAsync();
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

        Map<String, Class<?>> resultMap = new HashMap<>(keysMap.size());
        resultMap.putAll(keysMap);
        return resultMap;
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
