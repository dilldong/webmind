package org.mind.framework.interceptor;

import lombok.NoArgsConstructor;
import lombok.Setter;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.mind.framework.cache.CacheElement;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.service.Cloneable;
import org.mind.framework.util.MatcherUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

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

    public CacheinOperationInterceptor(Cacheable cacheable, Cloneable.CloneType cloneType, long expire) {
        this.cacheable = cacheable;
        this.cloneType = cloneType;
        this.expire = expire;
    }

    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        String resolverKey = resolverExpl(invocation.getArguments(), invocation.getThis(), invocation.getMethod(), key);
        CacheElement element = this.cacheable.getCache(resolverKey, expire);
        if (element != null) {
            if (log.isDebugEnabled())
                log.debug("Get by cache, key: [{}], visited: [{}]", element.getKey(), element.getVisited());

            return element.getValue(cloneType);
        }

        Object result = this.callback(invocation);
        if (Objects.nonNull(result)) {
            Class<? extends Object> clazz = result.getClass();
            boolean addCache = false;
            if (Collection.class.isAssignableFrom(clazz)) {
                if (!((Collection) result).isEmpty())
                    addCache = true;
            } else if (Map.class.isAssignableFrom(clazz)) {
                if (!((Map) result).isEmpty())
                    addCache = true;
            } else
                addCache = true;

            if (addCache)
                this.cacheable.addCache(resolverKey, result, true, cloneType);
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

        for (int i = 0; i < size; i++) {
            attrValue =
                    attrValue.replaceAll(
                            String.join("", "\\#\\{", methodVarNames[i], "\\}"),
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
