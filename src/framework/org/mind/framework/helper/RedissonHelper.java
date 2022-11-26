package org.mind.framework.helper;

import lombok.extern.slf4j.Slf4j;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.service.ExecutorFactory;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.util.JarFileUtils;
import org.redisson.Redisson;
import org.redisson.api.LockOptions;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2022/11/24
 */
@Slf4j
public class RedissonHelper {
    private static final String DEFAULT_REDISSON = "redisson.yml";
    private static final String JAR_REDISSON = "BOOT-INF/classes/redisson.yml";
    public static final String LOCK_PREFIX = "lock:";
    private final RedissonClient redissonClient;

    private static class Helper {
        private static final RedissonHelper INSTANCE = new RedissonHelper();
    }

    private RedissonHelper() {
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

        redissonClient = Redisson.create(config);
        Runtime.getRuntime().addShutdownHook(ExecutorFactory.newThread("Redisson-Gracefully", true, () -> {
            if (!redissonClient.isShutdown()) {
                log.info("Redisson-Gracefully is shutdowning ....");
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

    public <V> boolean set(String key, List<V> list, long expire, TimeUnit unit) {
        if (Objects.isNull(list) || list.isEmpty())
            return false;

        RList<V> rList = this.rList(key);
        if(!rList.isEmpty())
            rList.removeAll(rList);

        boolean flag = rList.addAll(list);
        if (flag) {
            if (TimeUnit.MILLISECONDS != unit)
                expire = unit.toMillis(expire);
            rList.expireAsync(Duration.of(expire, ChronoUnit.MILLIS));
        }
        return flag;
    }

    public <V> boolean set(String key, List<V> list, long expire, TimeUnit unit, RLock lock) {
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

    public <K, V> boolean set(String key, Map<K, V> map, long expire, TimeUnit unit) {
        if (Objects.isNull(map) || map.isEmpty())
            return false;

        RMap<K, V> rMap = this.rMap(key);
        if(!rMap.isEmpty())
            rMap.clear();

        rMap.putAll(rMap);
        if (TimeUnit.MILLISECONDS != unit)
            expire = unit.toMillis(expire);
        rMap.expireAsync(Duration.of(expire, ChronoUnit.MILLIS));
        return true;
    }

    public <K, V> boolean set(String key, Map<K, V> map, long expire, TimeUnit unit, RLock lock) {
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

        if (rMap.containsKey(k))
            rMap.remove(k);
    }

    public <K, V> void removeMap(String key, K k, RLock lock) {
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

    public <V> boolean set(String key, Set<V> list, long expire, TimeUnit unit) {
        if (Objects.isNull(list) || list.isEmpty())
            return false;

        RSet<V> rSet = this.rSet(key);
        if(!rSet.isEmpty())
            rSet.removeAll(rSet);

        boolean flag = rSet.addAll(list);
        if (flag) {
            if (TimeUnit.MILLISECONDS != unit)
                expire = unit.toMillis(expire);
            rSet.expireAsync(Duration.of(expire, ChronoUnit.MILLIS));
        }
        return flag;
    }

    public <V> boolean set(String key, Set<V> set, long expire, TimeUnit unit, RLock lock) {
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

    public <V> void removeSetAsync(String key, int index) {
        if (index < 0)
            return;

        RSet<V> rSet = this.rSet(key);
        if (rSet.size() > index)
            rSet.removeAsync(index);
    }

    public <V> void removeSet(String key, int index) {
        if (index < 0)
            return;

        RSet<V> rSet = this.rSet(key);
        if (rSet.size() > index)
            rSet.remove(index);
    }

    public <V> void removeSet(String key, int index, RLock lock) {
        lock.lock();
        try {
            this.removeSet(key, index);
        } finally {
            lock.unlock();
        }
    }

    public <V> void removeSetByLock(String key, int index) {
        if (index < 0)
            return;

        this.removeSet(key, index, this.getWriteLock(key));
    }

    public <V> V get(String key) {
        return (V) this.getClient().getBucket(key).get();
    }

    public <V> V getByLock(String key) {
        return this.get(key, this.getReadLock(key));
    }

    public <V> V get(String key, RLock lock) {
        lock.lock();
        try {
            return this.get(key);
        } finally {
            lock.unlock();
        }
    }

    public <V> V removeAsync(String key) {
        return (V) this.getClient().getBucket(key).getAndDeleteAsync();
    }

    public <V> V remove(String key) {
        return (V) this.getClient().getBucket(key).getAndDelete();
    }

    public <V> V remove(String key, RLock lock) {
        lock.lock();
        try {
            return this.remove(key);
        } finally {
            lock.unlock();
        }
    }

    public <V> V removeByLock(String key) {
        return this.remove(key, this.getWriteLock(key));
    }

    public <V> void setAysnc(String key, V value, long expire, TimeUnit unit) {
        RBucket<V> rBucket = this.getClient().getBucket(key);
        if (expire > 0) {
            rBucket.setAsync(value, expire, unit);
            return;
        }
        rBucket.setAsync(value);
    }

    public <V> void set(String key, V value, long expire, TimeUnit unit) {
        RBucket<V> rBucket = this.getClient().getBucket(key);
        if (expire > 0) {
            rBucket.set(value, expire, unit);
            return;
        }
        rBucket.set(value);
    }

    public <V> void setIfExists(String key, V value, long expire, TimeUnit unit) {
        RBucket<V> rBucket = this.getClient().getBucket(key);
        if (expire > 0) {
            rBucket.setIfExists(value, expire, unit);
            return;
        }
        rBucket.setIfExists(value);
    }

    public <V> void setIfAbsent(String key, V value, long expire, TimeUnit unit) {
        RBucket<V> rBucket = this.getClient().getBucket(key);
        if (expire > 0) {
            expire = TimeUnit.MILLISECONDS == unit ? expire : unit.toMillis(expire);
            rBucket.setIfAbsent(value, Duration.ofMillis(expire));
            return;
        }
        rBucket.setIfAbsent(value);
    }

    public <V> void setByLock(String key, V value, long expire, TimeUnit unit) {
        this.set(key, value, expire, unit, this.getWriteLock(key));
    }

    public <V> void set(String key, V value, long expire, TimeUnit unit, RLock Lock) {
        Lock.lock();
        try {
            this.set(key, value, expire, unit);
        } finally {
            Lock.unlock();
        }
    }

    public <V> void setIfExistsByLock(String key, V value, long expire, TimeUnit unit) {
        this.setIfExists(key, value, expire, unit, this.getWriteLock(key));
    }

    public <V> void setIfExists(String key, V value, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            this.setIfExists(key, value, expire, unit);
        } finally {
            lock.unlock();
        }
    }

    public <V> void setIfAbsentByLock(String key, V value, long expire, TimeUnit unit) {
        this.setIfAbsent(key, value, expire, unit, this.getWriteLock(key));
    }

    public <V> void setIfAbsent(String key, V value, long expire, TimeUnit unit, RLock lock) {
        // activating watch-dog
        lock.lock();
        try {
            this.setIfAbsent(key, value, expire, unit);
        } finally {
            lock.unlock();
        }
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

    private <V> RList<V> rList(String key) {
        return getClient().getList(key);
    }

    private <K, V> RMap<K, V> rMap(String key) {
        return getClient().<K, V>getMap(key);
    }

    private <V> RSet<V> rSet(String key) {
        return getClient().<V>getSet(key);
    }
}
