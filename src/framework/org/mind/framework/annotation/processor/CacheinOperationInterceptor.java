package org.mind.framework.annotation.processor;

import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.ArrayUtils;
import org.mind.framework.annotation.EnumFace;
import org.mind.framework.cache.CacheElement;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.service.Cloneable;
import org.mind.framework.util.MatcherUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2022/9/6
 */
@Setter
@NoArgsConstructor
public class CacheinOperationInterceptor implements MethodInterceptor {
    private static final Logger log = LoggerFactory.getLogger("org.mind.framework.annotation.Cachein");
    private static final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private String key;
    private long expire = 0;
    private Cloneable.CloneType cloneType;
    private Cacheable cacheable;
    private boolean penetration;
    private boolean inRedis;
    private TimeUnit timeUnit;
    private Class<? extends Object> redisType;

    public CacheinOperationInterceptor(Cacheable cacheable,
                                       Cloneable.CloneType cloneType,
                                       boolean penetration,
                                       long expire,
                                       TimeUnit timeUnit,
                                       boolean inRedis,
                                       Class<? extends Object>[] redisType) {
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

        if (List.class.isAssignableFrom(redisType)) {
            List<?> list = helper.getListWithLock(resolverKey);
            if (list.isEmpty()) {
                if (!this.penetration)
                    return list;
            } else
                return list;
        } else if (Map.class.isAssignableFrom(redisType)) {
            Map<?, ?> map = helper.getMapWithLock(resolverKey);
            if (map.isEmpty()) {
                if (!this.penetration)
                    return map;
            } else
                return map;
        } else if (Set.class.isAssignableFrom(redisType)) {
            Set<?> set = helper.getSetWithLock(resolverKey);
            if (set.isEmpty()) {
                if (!this.penetration)
                    return set;
            } else
                return set;
        } else {
            // for bucket
            Object obj = helper.getWithLock(resolverKey);
            if (Objects.isNull(obj)) {
                if (!this.penetration)
                    return null;
            } else
                return obj;
        }

        // invoke orig method
        Object result = this.callback(invocation);
        if(Objects.isNull(result))
            return null;

        if (result instanceof List) {
            helper.setWithLock(resolverKey, (List<?>) result, expire, timeUnit);
        } else if (result instanceof Map) {
            helper.setWithLock(resolverKey, (Map<?, ?>) result, expire, timeUnit);
        } else if (result instanceof Set) {
            helper.setWithLock(resolverKey, (Set<?>) result, expire, timeUnit);
        } else
            helper.setWithLock(resolverKey, result, expire, timeUnit);

        return result;
    }


    private Object forLocal(String resolverKey, MethodInvocation invocation) throws Throwable {
        CacheElement element = this.cacheable.getCache(
                resolverKey,
                TimeUnit.MILLISECONDS == timeUnit ? expire : timeUnit.toMillis(expire));
        if (Objects.isNull(element)) {
            if (!this.penetration)
                return null;
        } else {
            if (log.isDebugEnabled())
                log.debug("Get by cache, key: [{}], visited: [{}]", element.getKey(), element.getVisited());

            return element.getValue(cloneType);
        }

        Object result = this.callback(invocation);
        if (Objects.nonNull(result)) {
            Class<?> clazz = result.getClass();
            boolean addCache = true;

            if (Collection.class.isAssignableFrom(clazz))
                addCache = !((Collection<?>) result).isEmpty();
            else if (Map.class.isAssignableFrom(clazz))
                addCache = !((Map<?, ?>) result).isEmpty();

            if (addCache)
                this.cacheable.addCache(resolverKey, result, true, cloneType);
        }

        return result;
    }

    private String resolverExpl(Object[] params, Object target, Method method, String attrKey) {
        if (ArrayUtils.isEmpty(params))
            return attrKey;

        if (MatcherUtils.checkCount(attrKey, MatcherUtils.PARAM_MATCH_PATTERN) == 0)
            return attrKey;

        String[] methodVarNames = parameterNameDiscoverer.getParameterNames(method);
        if (Objects.isNull(methodVarNames)) {
            try {
                Method m = target.getClass().getMethod(method.getName(), method.getParameterTypes());
                methodVarNames = parameterNameDiscoverer.getParameterNames(m);
            } catch (NoSuchMethodException ignored) {}
        }

        Objects.requireNonNull(methodVarNames);
        int size = params.length;

        for (int i = 0; i < size; ++i) {
            attrKey =
                    attrKey.replaceAll(
                            "#\\{" + methodVarNames[i] + "\\}",
                            params[i] instanceof EnumFace ?
                                    String.valueOf(((EnumFace<? extends Serializable>) params[i]).getValue()) :
                                    String.valueOf(params[i]));
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
}
