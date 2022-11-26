package org.mind.framework.interceptor;

import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
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
    private boolean inRedis;
    private TimeUnit timeUnit;
    private Class<? extends Object> returnType;

    public CacheinOperationInterceptor(Cacheable cacheable,
                                       Cloneable.CloneType cloneType,
                                       long expire,
                                       TimeUnit timeUnit,
                                       boolean inRedis,
                                       Class<? extends Object> returnType) {
        this.cacheable = cacheable;
        this.cloneType = cloneType;
        this.expire = expire;
        this.timeUnit = timeUnit;
        this.inRedis = inRedis;
        this.returnType = returnType;
    }

    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        String resolverKey = resolverExpl(invocation.getArguments(), invocation.getThis(), invocation.getMethod(), key);
        if (this.inRedis)
            return forRedis(resolverKey, invocation);

        return forLocal(resolverKey, invocation);
    }

    private Object forRedis(String resolverKey, MethodInvocation invocation) throws Throwable {
        Objects.requireNonNull(returnType, "Should specify the return type when getting the cache from redis.");
        RedissonHelper helper = RedissonHelper.getInstance();

        if (List.class.isAssignableFrom(returnType)) {
            List list = helper.getListByLock(resolverKey);
            if(!list.isEmpty())
                return list;
        } else if (Map.class.isAssignableFrom(returnType)) {
            Map map = helper.getMapByLock(resolverKey);
            if(!map.isEmpty())
                return map;
        } else if (Set.class.isAssignableFrom(returnType)) {
            Set set = helper.getSetByLock(resolverKey);
            if(!set.isEmpty())
                return set;
        }

        // for bucket
        Object obj = helper.getByLock(resolverKey);
        if (Objects.nonNull(obj))
            return obj;

        Object result = this.callback(invocation);
        if (Objects.nonNull(result)) {
            Class<? extends Object> clazz = result.getClass();
            if (List.class.isAssignableFrom(clazz)) {
                helper.setByLock(resolverKey, (List)result, expire, timeUnit);
            } else if (Map.class.isAssignableFrom(clazz)) {
                helper.setByLock(resolverKey, (Map)result, expire, timeUnit);
            } else if (Set.class.isAssignableFrom(clazz)) {
                helper.setByLock(resolverKey, (Set)result, expire, timeUnit);
            } else
                helper.setByLock(resolverKey, result, expire, timeUnit);
        }

        return result;
    }


    private Object forLocal(String resolverKey, MethodInvocation invocation) throws Throwable {
        CacheElement element = this.cacheable.getCache(resolverKey, expire);
        if (element != null) {
            if (log.isDebugEnabled())
                log.debug("Get by cache, key: [{}], visited: [{}]", element.getKey(), element.getVisited());

            return element.getValue(cloneType);
        }

        Object result = this.callback(invocation);
        if (Objects.nonNull(result)) {
            Class<? extends Object> clazz = result.getClass();
            boolean addCache = true;

            if (Collection.class.isAssignableFrom(clazz)) {
                addCache = !((Collection) result).isEmpty();
            } else if (Map.class.isAssignableFrom(clazz)) {
                addCache = !((Map) result).isEmpty();
            }

            if (addCache) {
                this.cacheable.addCache(resolverKey, result, true, cloneType);
            }
        }

        return result;
    }

    private String resolverExpl(Object[] params, Object target, Method method, String attrValue) {
        if (Objects.isNull(params) || params.length == 0)
            return attrValue;

        if (MatcherUtils.checkCount(attrValue, MatcherUtils.PARAM_MATCH) == 0)
            return attrValue;

        String[] methodVarNames = parameterNameDiscoverer.getParameterNames(method);
        if (Objects.isNull(methodVarNames)) {
            try {
                Method m = target.getClass().getMethod(method.getName(), method.getParameterTypes());
                methodVarNames = parameterNameDiscoverer.getParameterNames(m);
            } catch (NoSuchMethodException e) {
            }
        }

        Objects.requireNonNull(methodVarNames);
        int size = params.length;

        for (int i = 0; i < size; ++i) {
            attrValue =
                    attrValue.replaceAll(
                            "\\#\\{".concat(methodVarNames[i]).concat("\\}"),
                            EnumFace.class.isAssignableFrom(params[i].getClass()) ?
                                    ((EnumFace<String>) params[i]).getValue() :
                                    String.valueOf(params[i]));
        }

        return attrValue;
    }

    private Object callback(MethodInvocation invocation) throws Exception {
        if (invocation instanceof ProxyMethodInvocation) {
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
