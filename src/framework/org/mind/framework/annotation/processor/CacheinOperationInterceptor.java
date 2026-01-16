package org.mind.framework.annotation.processor;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.annotation.CacheinFace;
import org.mind.framework.cache.AbstractCache;
import org.mind.framework.cache.CacheElement;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.exception.NotSupportedException;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.service.Cloneable;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.web.dispatcher.support.ConverterFactory;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
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
 * @version 1.0
 * @author Marcus
 * @date 2022/9/6
 */
@Setter
@NoArgsConstructor
public class CacheinOperationInterceptor implements MethodInterceptor {
    private static final Logger log = LoggerFactory.getLogger("org.mind.framework.annotation.Cachein");
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final Map<Class<?>, String> NULL_TYPE_MAP = new HashMap<>(3);

    private String key;
    private long expire = 0;
    private Cloneable.CloneType cloneType;
    private Cacheable cacheable;
    private boolean penetration;
    private boolean inRedis;
    private TimeUnit timeUnit;
    private Class<?> redisType;

    static {
        NULL_TYPE_MAP.put(List.class, RedissonHelper.EMPTY_LIST_MARKER);
        NULL_TYPE_MAP.put(Map.class, RedissonHelper.EMPTY_MAP_MARKER);
        NULL_TYPE_MAP.put(Set.class, RedissonHelper.EMPTY_SET_MARKER);
    }

    public CacheinOperationInterceptor(Cacheable cacheable,
                                       Cloneable.CloneType cloneType,
                                       boolean penetration,
                                       long expire,
                                       TimeUnit timeUnit,
                                       boolean inRedis,
                                       Class<?>[] redisType) {
        this.cacheable = cacheable;
        this.cloneType = cloneType;
        this.penetration = penetration;
        this.expire = expire;
        this.timeUnit = timeUnit;
        this.inRedis = inRedis;
        this.redisType = ArrayUtils.isEmpty(redisType) ? null : redisType[0];
    }

    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        String resolverKey = resolverExpl(invocation.getArguments(), invocation.getThis(), invocation.getMethod(), key);
        if (this.inRedis)
            return forRedis(resolverKey, invocation);

        return forLocal(resolverKey, invocation);
    }

    private Object forRedis(String resolverKey, MethodInvocation invocation) throws Throwable {
        Objects.requireNonNull(redisType, "Should specify the return type when getting the cache from redis.");
        RedissonHelper helper = RedissonHelper.getInstance();

        TypeMatchResult typeMatch = getNullTypeValue(redisType);

        ResolveResult cacheResult = resolveCacheValue(
                typeMatch.getNullMarker(),
                () -> helper.getWithLock(resolverKey),// 用于验证是否为NULL marker
                () -> typeMatch.loadReids(resolverKey),
                typeMatch.getEmptyValue()
        );

        // penetration=false时，允许返回空值
        if (cacheResult.isShouldShortCircuit())
            return cacheResult.getResult();

        if (!isEmpty(cacheResult.getResult()))
            return cacheResult.getResult();

        // invoke orig method
        Object result = this.callback(invocation);
        if (isEmpty(result) && this.penetration)
            return result;

        if (result instanceof List) {
            if (((List<?>) result).isEmpty()) {
                if (!this.penetration)
                    helper.setWithLock(resolverKey, RedissonHelper.EMPTY_LIST_MARKER, expire, timeUnit);
            } else
                helper.setWithLock(resolverKey, (List<?>) result, expire, timeUnit);
        } else if (result instanceof Map) {
            if (((Map<?, ?>) result).isEmpty()) {
                if (!this.penetration)
                    helper.setWithLock(resolverKey, RedissonHelper.EMPTY_MAP_MARKER, expire, timeUnit);
            } else
                helper.setWithLock(resolverKey, (Map<?, ?>) result, expire, timeUnit);
        } else if (result instanceof Set) {
            if (((Set<?>) result).isEmpty()) {
                if (!this.penetration)
                    helper.setWithLock(resolverKey, RedissonHelper.EMPTY_SET_MARKER, expire, timeUnit);
            } else
                helper.setWithLock(resolverKey, (Set<?>) result, expire, timeUnit);
        } else {
            // 当result == null时，这里penetration=false，需要设置null marker
            helper.setWithLock(resolverKey, Objects.isNull(result) ? RedissonHelper.NULL_MARKER : result, expire, timeUnit);
        }

        return result;
    }

    private Object forLocal(String resolverKey, MethodInvocation invocation) throws Throwable {
        CacheElement element = this.cacheable.getCache(
                resolverKey,
                TimeUnit.MILLISECONDS == timeUnit ? expire : timeUnit.toMillis(expire));

        if (Objects.isNull(element)) {
            if (!this.penetration && this.cacheable.containsKey(resolverKey))
                return null;
        } else {
            if (log.isDebugEnabled())
                log.debug("Get by cache, key: [{}], visited: [{}]", element.getKey(), element.getVisited());

            Object result = element.getValue(cloneType);
            if (!this.penetration)
                return Objects.equals(RedissonHelper.NULL_MARKER, result) ? null : result;
            return result;
        }

        Object result = this.callback(invocation);

        if (isEmpty(result)) {
            if (this.penetration)
                return result;

            result = RedissonHelper.NULL_MARKER;
        }

        this.cacheable.addCache(resolverKey, result, true, cloneType);
        return Objects.equals(RedissonHelper.NULL_MARKER, result) ? null : result;
    }

    private String resolverExpl(Object[] params, Object target, Method method, String attrKey) {
        if (ArrayUtils.isEmpty(params))
            return attrKey;

        if (MatcherUtils.checkCount(attrKey, MatcherUtils.PARAM_MATCH_PATTERN) == 0)
            return attrKey;

        // AopProxyUtils.ultimateTargetClass 穿透多层代理，返回原始对象
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(target);

        Method originalMethod;
        try {
            originalMethod = targetClass.getDeclaredMethod(method.getName(), method.getParameterTypes());
            originalMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            // 回退到原 method（罕见情况）
            originalMethod = method;
        }

        String[] paramNames = PARAMETER_NAME_DISCOVERER.getParameterNames(originalMethod);

        Objects.requireNonNull(paramNames);
        int size = params.length;

        for (int i = 0; i < size; ++i) {
            String value = null;
            if (Objects.nonNull(params[i])) {
                if (ConverterFactory.getInstance().isConvert(params[i].getClass()))
                    value = String.valueOf(params[i]);
                else if (params[i] instanceof CacheinFace)
                    value = String.valueOf(((CacheinFace<? extends Serializable>) params[i]).getValue());
                else if (params[i].getClass().isArray())
                    value = arrayToString(params[i]);
                else if (params[i] instanceof Collection) {
                    value = ((Collection<?>) params[i]).stream().map(v -> {
                        if (v instanceof CacheinFace)
                            return String.valueOf(((CacheinFace<? extends Serializable>) v).getValue());
                        return String.valueOf(v);
                    }).collect(Collectors.joining(AbstractCache.CACHE_DELIMITER));
                } else {
                    throw new NotSupportedException("Key value conversion failed. Supported types: basic-types, one-dimensional arrays(basic-types), CacheinFace, Collection(basic-types and CacheinFace");
                }
            }

            attrKey =
                    attrKey.replaceAll(
                            "#\\{" + paramNames[i] + "\\}",
                            StringUtils.defaultIfEmpty(value, StringUtils.EMPTY));
        }

        return attrKey;
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
        } else
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
                    .collect(Collectors.joining(AbstractCache.CACHE_DELIMITER));
        }

        // Object type array
        return
                Stream.of((Object[]) obj).map(v -> {
                    if (v instanceof CacheinFace)
                        return String.valueOf(((CacheinFace<? extends Serializable>) v).getValue());
                    return String.valueOf(v);
                }).collect(Collectors.joining(AbstractCache.CACHE_DELIMITER));
    }

    private TypeMatchResult getNullTypeValue(Class<?> redisType) {
        for (Map.Entry<Class<?>, String> entry : NULL_TYPE_MAP.entrySet()) {
            if (entry.getKey().isAssignableFrom(redisType))
                return TypeMatchResult.of(entry.getKey(), entry.getValue());
        }
        return TypeMatchResult.of(RedissonHelper.NULL_MARKER);
    }

    private <T> ResolveResult resolveCacheValue(
            String nullMarker,
            Supplier<Object> rawGetter,
            Supplier<T> dataGetter,
            T emptyValue) {

        boolean isNullValue = false;
        if (!this.penetration) {
            try {
                Object value = rawGetter.get();
                if (Objects.equals(nullMarker, value))
                    return new ResolveResult(emptyValue, true);  // 提前中断

                isNullValue = Objects.isNull(value);
            } catch (RuntimeException ignored) {
            }
        }

        T result = isNullValue ? emptyValue : dataGetter.get();
        if (Objects.equals(nullMarker, result))
            return new ResolveResult(emptyValue, false);
        return new ResolveResult(result, false);
    }

    @SuppressWarnings("rawtypes")
    private boolean isEmpty(Object obj) {
        if (Objects.isNull(obj))
            return true;

        if (obj instanceof Collection)
            return ((Collection) obj).isEmpty();

        if (obj instanceof Map)
            return ((Map) obj).isEmpty();

        return false;
    }

    @AllArgsConstructor
    private static class TypeMatchResult {
        private final Class<?> matchedClass;
        @Getter
        private final String nullMarker;

        public Object loadReids(String name) {
            RedissonHelper helper = RedissonHelper.getInstance();
            try {
                if (isListType())
                    return helper.getListWithLock(name);
                else if (isMapType())
                    return helper.getMapWithLock(name);
                else if (isSetType())
                    return helper.getSetWithLock(name);
            }catch (RedisException ignored){}
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
            return new TypeMatchResult(null, value);
        }

        public static TypeMatchResult of(Class<?> matchedClass, String value) {
            return new TypeMatchResult(matchedClass, value);
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

    @Getter
    @AllArgsConstructor
    public static class ResolveResult {
        private final Object result;
        private final boolean shouldShortCircuit;
    }
}
