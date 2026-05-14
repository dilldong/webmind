package org.mind.framework.annotation.processor;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.annotation.CacheLevel;
import org.mind.framework.annotation.CacheinFace;
import org.mind.framework.cache.AbstractCache;
import org.mind.framework.cache.CacheElement;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.exception.NotSupportedException;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.service.Cloneable;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.web.dispatcher.support.ConverterFactory;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import java.io.Serializable;
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
@Setter
@NoArgsConstructor
public class CacheinOperationInterceptor implements MethodInterceptor {
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final Map<Class<?>, String> NULL_TYPE_MAP = new HashMap<>(3);

    private String key;
    private long expire = 0L;
    private TimeUnit timeUnit;
    private Cacheable cacheable;
    private Cloneable.CloneType cloneType;
    private boolean cacheNull;
    private CacheLevel[] cacheLevels;

    static {
        NULL_TYPE_MAP.put(List.class, RedissonHelper.EMPTY_LIST_MARKER);
        NULL_TYPE_MAP.put(Map.class, RedissonHelper.EMPTY_MAP_MARKER);
        NULL_TYPE_MAP.put(Set.class, RedissonHelper.EMPTY_SET_MARKER);
    }

    public CacheinOperationInterceptor(Cacheable cacheable,
                                       Cloneable.CloneType cloneType,
                                       boolean cacheNull,
                                       long expire,
                                       TimeUnit timeUnit,
                                       CacheLevel[] cacheLevels) {
        this.cacheable = cacheable;
        this.cloneType = cloneType;
        this.cacheNull = cacheNull;
        this.expire = expire;
        this.timeUnit = timeUnit;
        this.cacheLevels = cacheLevels;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String resolverKey = resolverExpl(invocation.getArguments(), invocation.getThis(), invocation.getMethod(), key);

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
            if (result.isShouldShortCircuit() || !isEmpty(result.getResult()))
                return result.getResult();
        }

        // for redis
        boolean isRedis =
                Arrays.stream(cacheLevels)
                        .anyMatch(v -> CacheLevel.REDIS == v);
        if (isRedis) {
            ResolveResult result = forRedis(resolverKey, nullTypeValue);

            if (result.isShouldShortCircuit() || !isEmpty(result.getResult())) {
                if (isLocal)
                    save2local(resolverKey, result.getResult(), nullTypeValue);
                return result.getResult();
            }
        }

        // for implementation
        Object result = this.callback(invocation);

        if (isLocal)
            save2local(resolverKey, result, nullTypeValue);

        if (isRedis)
            save2redis(resolverKey, result, nullTypeValue);

        return result;
    }

    private ResolveResult forRedis(String resolverKey, TypeMatchResult nullTypeValue){
        return resolveCacheValue(
                nullTypeValue.getNullMarker(),
                () -> RedissonHelper.getInstance().getWithLock(resolverKey),// 用于验证: NULL marker
                () -> nullTypeValue.loadReids(resolverKey),                 // 加载结果
                nullTypeValue.getEmptyValue()
        );
    }

    private ResolveResult forLocal(String resolverKey, TypeMatchResult nullTypeValue){
        CacheElement element = this.cacheable.getCache(resolverKey, timeUnit.toMillis(expire));
        if (Objects.isNull(element))
            return new ResolveResult(nullTypeValue.getEmptyValue(), false);

        Object result = element.getValue(cloneType);
        return resolveCacheValue(
                nullTypeValue.getNullMarker(),
                () -> result,// 用于验证: NULL marker
                () -> result,// 加载结果
                nullTypeValue.getEmptyValue()
        );
    }

    private String resolverExpl(Object[] params, Object target, Method method, String attrKey) {
        if (ArrayUtils.isEmpty(params))
            return attrKey;

        if (MatcherUtils.checkCount(attrKey, MatcherUtils.PARAM_MATCH_PATTERN) == 0)
            return attrKey;

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


    private void save2local(String resolverKey, Object result, TypeMatchResult nullTypeValue) {
        if (isEmpty(result)) {
            if (this.cacheNull) {
                cacheable.addCache(
                        resolverKey,
                        new CacheElement(nullTypeValue.getNullMarker(), resolverKey, timeUnit.toMillis(expire), cloneType),
                        true
                );
            }
            return;
        }

        cacheable.addCache(
                resolverKey,
                new CacheElement(result, resolverKey, timeUnit.toMillis(expire), cloneType),
                true
        );
    }

    private void save2redis(String resolverKey, Object result, TypeMatchResult nullTypeValue) {
        if (isEmpty(result)) {
            if (this.cacheNull)
                RedissonHelper.getInstance().setWithLock(resolverKey, nullTypeValue.getNullMarker(), expire, timeUnit);
            return;
        }

        RedissonHelper.getInstance().setWithLock(resolverKey, result, expire, timeUnit);
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

    private TypeMatchResult getNullTypeValue(Class<?> returnType) {
        for (Map.Entry<Class<?>, String> entry : NULL_TYPE_MAP.entrySet()) {
            if (entry.getKey().isAssignableFrom(returnType))
                return TypeMatchResult.of(entry.getValue());
        }
        return TypeMatchResult.of(RedissonHelper.NULL_MARKER);
    }

    private <T> ResolveResult resolveCacheValue(
            String nullMarker,
            Supplier<Object> rawGetter,
            Supplier<T> dataGetter,
            T emptyValue) {

        boolean isNullValue = false;
        if (this.cacheNull) {
            Object value = rawGetter.get();
            if (Objects.equals(nullMarker, value))
                return new ResolveResult(emptyValue, true);  // 提前中断

            isNullValue = Objects.isNull(value);
        }

        T result = isNullValue ? emptyValue : dataGetter.get();
        if (Objects.equals(nullMarker, result))
            return new ResolveResult(emptyValue, false);

        return new ResolveResult(result, false);
    }

    @SuppressWarnings("rawtypes")
    private boolean isEmpty(Object value) {
        if (Objects.isNull(value))
            return true;

        if (value instanceof Collection)
            return ((Collection) value).isEmpty();

        if (value instanceof Map)
            return ((Map) value).isEmpty();

        return false;
    }

    @AllArgsConstructor
    private static class TypeMatchResult {
        @Getter
        private final String nullMarker;

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

    @Getter
    @AllArgsConstructor
    public static class ResolveResult {
        private final Object result;
        private final boolean shouldShortCircuit;
    }
}
