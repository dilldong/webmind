package org.mind.framework.annotation.processor;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.annotation.BeaconGuard;
import org.mind.framework.annotation.CacheLevel;
import org.mind.framework.annotation.Cachein;
import org.mind.framework.annotation.CacheinFace;
import org.mind.framework.cache.CacheElement;
import org.mind.framework.cache.CacheEventPublisher;
import org.mind.framework.cache.CacheUtils;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.exception.NotSupportedException;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.service.Cloneable;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.web.dispatcher.support.ConverterFactory;
import org.redisson.api.RLock;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * @author Marcus
 * @version 1.0
 * @date 2022/9/6
 */
@Slf4j(topic = "Cachein")
public class CacheinOperationInterceptor implements MethodInterceptor {
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final Map<Class<?>, String> NULL_TYPE_MAP = new HashMap<>(3);

    private final String staticKey;
    private final long expire;
    private final TimeUnit timeUnit;
    private final Cacheable cacheable;
    private final Cloneable.CloneType cloneType;
    private final boolean cacheNull;
    private final CacheLevel[] cacheLevels;
    private final CacheEventPublisher eventPublisher;
    private final String delimiter;
    private final BeaconGuard beaconGuard;

    static {
        NULL_TYPE_MAP.put(List.class, RedissonHelper.EMPTY_LIST_MARKER);
        NULL_TYPE_MAP.put(Map.class, RedissonHelper.EMPTY_MAP_MARKER);
        NULL_TYPE_MAP.put(Set.class, RedissonHelper.EMPTY_SET_MARKER);
    }

    public CacheinOperationInterceptor(Cacheable cacheable,
                                       Cachein cachein,
                                       CacheLevel[] cacheLevels,
                                       CacheEventPublisher eventPublisher,
                                       String staticKey) {
        this.cacheable = cacheable;
        this.cloneType = cachein.strategy();
        this.cacheNull = cachein.cacheNull();
        this.expire = Math.max(cachein.expire(), 0L);
        this.timeUnit = cachein.unit();
        this.cacheLevels = cacheLevels;
        this.delimiter = cachein.delimiter();
        this.beaconGuard = cachein.beaconGuard();
        this.staticKey = staticKey;
        this.eventPublisher = eventPublisher;

        if(this.beaconGuard.exclusive()){
            boolean isRedis =
                    Arrays.stream(cacheLevels)
                            .anyMatch(v -> CacheLevel.REDIS == v);

            if(!isRedis)
                throw new IllegalArgumentException("When exclusive=true, the levels must include REDIS.");
        }
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String resolverKey = resolverExpl(invocation.getArguments(), invocation.getThis(), invocation.getMethod());

        if (ArrayUtils.isEmpty(cacheLevels))
            return this.callback(invocation);

        // Deduced return type
        TypeMatchResult nullTypeValue = this.getNullTypeValue(invocation.getMethod().getReturnType());
        Objects.requireNonNull(nullTypeValue, "The method should specify a return type");

        // for local
        boolean isLocal =
                Arrays.stream(cacheLevels)
                        .anyMatch(v -> CacheLevel.LOCAL == v);
        if (isLocal) {
            ResolveResult result = forLocal(resolverKey, nullTypeValue);
            if (result.shouldShortCircuit() || !CacheUtils.isEmpty(result.result()))
                return result.result();
        }

        // for redis
        boolean isRedis =
                Arrays.stream(cacheLevels)
                        .anyMatch(v -> CacheLevel.REDIS == v);
        if (isRedis) {
            ResolveResult result = forRedis(resolverKey, nullTypeValue);

            if (result.shouldShortCircuit() || !CacheUtils.isEmpty(result.result())) {
                if (isLocal)
                    save2local(resolverKey, result.result(), nullTypeValue, true);
                return result.result();
            }
        }

        // for implementation
        if(beaconGuard.exclusive() && isRedis)
            return loadWithGuard(resolverKey, invocation, nullTypeValue, isLocal);

        return loader(resolverKey, invocation, nullTypeValue, isRedis, isLocal);
    }

    // Local source
    private Object loader(String key,
                          MethodInvocation invocation,
                          TypeMatchResult nullTypeValue,
                          boolean isRedis,
                          boolean isLocal) throws Exception {
        Object result = this.callback(invocation);
        if (isRedis)
            save2redis(key, result, nullTypeValue);

        if (isLocal)
            save2local(key, result, nullTypeValue);

        return result;
    }

    private Object loadWithGuard(String key,
                                 MethodInvocation invocation,
                                 TypeMatchResult nullTypeValue,
                                 boolean isLocal) throws Exception {

        // The same CacheUtils.loadWithGuard()
        RLock lock = RedissonHelper.getInstance().getLock(key + CacheUtils.BACKFILL_CACHE);
        try {
            if (lock.tryLock(beaconGuard.waitTime(), beaconGuard.timeUnit())) {
                try {
                    ResolveResult again = forRedis(key, nullTypeValue);
                    if (again.shouldShortCircuit() || !CacheUtils.isEmpty(again.result())) {
                        if(isLocal)
                            save2local(key, again.result(), nullTypeValue, true);
                        return again.result();
                    }

                    return loader(key, invocation, nullTypeValue, true, isLocal);
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
        ResolveResult again = forRedis(key, nullTypeValue);
        if (again.shouldShortCircuit() || !CacheUtils.isEmpty(again.result())) {
            if(isLocal)
                save2local(key, again.result(), nullTypeValue, true);
            return again.result();
        }

        log.warn("Cache load lock timeout({} {}), degrade to local load: {}",
                beaconGuard.waitTime(), beaconGuard.timeUnit(), key);

        // degrade to L1-only (与 CacheUtils 对齐)
        Object result = this.callback(invocation);
        if(isLocal)
            save2local(key, result, nullTypeValue);
        return result;
    }

    private ResolveResult forRedis(String resolverKey, TypeMatchResult nullTypeValue) {
        return resolveCacheValue(
                nullTypeValue.nullMarker(),
                () -> nullTypeValue.loadReids(resolverKey),// Loader
                nullTypeValue.getEmptyValue()
        );
    }

    private ResolveResult forLocal(String resolverKey, TypeMatchResult nullTypeValue) {
        CacheElement element = this.cacheable.getCache(resolverKey, timeUnit.toMillis(expire));
        if (Objects.isNull(element))
            return new ResolveResult(nullTypeValue.getEmptyValue(), false);

        Object result = element.getValue(cloneType);
        return resolveCacheValue(
                nullTypeValue.nullMarker(),
                () -> result,// Loader
                nullTypeValue.getEmptyValue()
        );
    }

    private String resolverExpl(Object[] params, Object target, Method method) {
        if (ArrayUtils.isEmpty(params))
            return staticKey;

        if (MatcherUtils.checkCount(staticKey, MatcherUtils.PARAM_MATCH_PATTERN) == 0)
            return staticKey;

        /*
         * 使用 Spring 提供的方法解析 + 参数名发现器
         * 接口 → 实现类
         * bridge method
         * 泛型擦除问题
         */
        Method specificMethod = AopUtils.getMostSpecificMethod(method, target.getClass());
        String[] paramNames = PARAMETER_NAME_DISCOVERER.getParameterNames(specificMethod);

        Objects.requireNonNull(paramNames);
        int size = params.length;
        String resolveKey = staticKey;

        for (int i = 0; i < size; ++i) {
            String value = null;
            if (Objects.nonNull(params[i])) {
                if (ConverterFactory.getInstance().isConvert(params[i].getClass()))
                    value = String.valueOf(params[i]);
                else if (params[i] instanceof CacheinFace<?> v)
                    value = String.valueOf(v.getValue());
                else if (params[i].getClass().isArray())
                    value = arrayToString(params[i]);
                else if (params[i] instanceof Collection<?> collection) {
                    value = collection.stream().map(v -> {
                        if (v instanceof CacheinFace<?> face)
                            return String.valueOf(face.getValue());
                        return String.valueOf(v);
                    }).collect(Collectors.joining(delimiter));
                } else {
                    throw new NotSupportedException("Key value conversion failed. Supported types: basic-types, one-dimensional arrays(basic-types), CacheinFace, Collection(basic-types and CacheinFace");
                }
            }

            resolveKey =
                    resolveKey.replaceAll(
                            "#\\{" + paramNames[i] + "\\}",
                            StringUtils.defaultIfEmpty(value, StringUtils.EMPTY));
        }

        return resolveKey;
    }

    private void save2local(String resolverKey, Object result, TypeMatchResult nullTypeValue, boolean... remainTimeToLive) {
        long remainMs = ArrayUtils.isEmpty(remainTimeToLive) || !remainTimeToLive[0] ?
                timeUnit.toMillis(expire) :
                RedissonHelper.getInstance().rBucket(resolverKey).remainTimeToLive();

        // -2 表示 key 在读值和查 TTL 之间刚好消失
        // -1 表示无过期时间
        if(remainMs < -1)
            return;

        if (CacheUtils.isEmpty(result)) {
            if (this.cacheNull) {
                cacheable.addCache(
                        resolverKey,
                        new CacheElement(nullTypeValue.nullMarker(), resolverKey, Math.max(remainMs, 0L), cloneType),
                        true
                );
            }
            return;
        }

        cacheable.addCache(
                resolverKey,
                new CacheElement(result, resolverKey, Math.max(remainMs, 0L), cloneType),
                true
        );
    }

    private void save2redis(String resolverKey, Object result, TypeMatchResult nullTypeValue) {
        long expireTime = expire <= 0L? -1L : expire;
        if (CacheUtils.isEmpty(result)) {
            if (this.cacheNull) {
                RedissonHelper.getInstance().setWithLock(resolverKey, nullTypeValue.nullMarker(), expireTime, timeUnit);
                eventPublisher.publish(resolverKey, expire, timeUnit);
            }
            return;
        }

        // 需要验证 result 类型，便于在 redis 中存储时指定类型
        if (result instanceof List<?> v)
            RedissonHelper.getInstance().setWithLock(resolverKey, v, expireTime, timeUnit);
        else if (result instanceof Set<?> v)
            RedissonHelper.getInstance().setWithLock(resolverKey, v, expireTime, timeUnit);
        else if (result instanceof Map<?, ?> v)
            RedissonHelper.getInstance().setWithLock(resolverKey, v, expireTime, timeUnit);
        else
            RedissonHelper.getInstance().setWithLock(resolverKey, result, expireTime, timeUnit);

        eventPublisher.publish(resolverKey, expire, timeUnit);
    }

    private Object callback(MethodInvocation invocation) throws Exception {
        if (ProxyMethodInvocation.class.isAssignableFrom(invocation.getClass())) {
            try {
                return ((ProxyMethodInvocation) invocation).invocableClone().proceed();
            } catch (Exception | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        }

        throw new IllegalStateException("MethodInvocation of the wrong type detected - this should not happen with Spring AOP.");
    }

    private String arrayToString(Object obj) {
        int length = ArrayUtils.getLength(obj);
        if (length == 0)
            return StringUtils.EMPTY;

        // If a basic type array
        if (obj.getClass().getComponentType().isPrimitive()) {
            return IntStream.range(0, length)
                    .mapToObj(i -> String.valueOf(Array.get(obj, i)))
                    .collect(Collectors.joining(delimiter));
        }

        // Object type array
        return Stream.of((Object[]) obj).map(v -> {
            if (v instanceof CacheinFace<?> face)
                return String.valueOf(face.getValue());
            return String.valueOf(v);
        }).collect(Collectors.joining(delimiter));
    }

    private TypeMatchResult getNullTypeValue(Class<?> returnType) {
        for (Map.Entry<Class<?>, String> entry : NULL_TYPE_MAP.entrySet()) {
            if (entry.getKey().isAssignableFrom(returnType))
                return TypeMatchResult.of(entry.getValue());
        }
        return TypeMatchResult.of(RedissonHelper.NULL_MARKER);
    }

    private ResolveResult resolveCacheValue(
            String nullMarker,
            Supplier<Object> dataLoader,
            Object emptyValue) {

        Object result = dataLoader.get();
        if (Objects.equals(nullMarker, result))
            return new ResolveResult(emptyValue, cacheNull);

        return new ResolveResult(result == null? emptyValue : result, false);
    }

    private record TypeMatchResult(String nullMarker) {
        public Object loadReids(String name) {
            RedissonHelper helper = RedissonHelper.getInstance();
            if (isListType())
                return helper.getListWithLock(name);
            else if (isMapType())
                return helper.getMapWithLock(name);
            else if (isSetType())
                return helper.getSetWithLock(name);
            else
                return helper.getWithLock(name);
        }

        public <T> T getEmptyValue() {
            if (isListType())
                return (T) Collections.emptyList();
            else if (isMapType())
                return (T) Collections.emptyMap();
            else if (isSetType())
                return (T) Collections.emptySet();
            else
                return null;
        }

        public static TypeMatchResult of(String value) {
            return new TypeMatchResult(value);
        }

        public boolean isListType() {
            return RedissonHelper.EMPTY_LIST_MARKER.equals(nullMarker);
        }

        public boolean isMapType() {
            return RedissonHelper.EMPTY_MAP_MARKER.equals(nullMarker);
        }

        public boolean isSetType() {
            return RedissonHelper.EMPTY_SET_MARKER.equals(nullMarker);
        }

    }

    public record ResolveResult(Object result, boolean shouldShortCircuit) {}
}