package org.mind.framework.annotation.processor;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.mind.framework.annotation.CacheLevel;
import org.mind.framework.annotation.Cacheout;
import org.mind.framework.cache.CacheUtils;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.util.TransactionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 *
 * @author Marcus
 * @version 1.0
 * @date 2026/10/24
 */
@Slf4j(topic = "Cacheout")
public class CacheoutOperationInterceptor implements MethodInterceptor {

    private final Cacheable cacheable;
    private final CacheLevel[] cacheLevels;
    private final Method method;
    private final Class<?> targetClass;
    private final Cacheout cacheout;

    private final String staticKey;
    private final ConditionEvaluator conditionEvaluator;

    public CacheoutOperationInterceptor(Cacheable cacheable,
                                        Cacheout cacheout,
                                        CacheLevel[] cacheLevels,
                                        Method method,
                                        Class<?> targetClass,
                                        ConditionEvaluator conditionEvaluator) {
        this.cacheable = cacheable;
        this.staticKey = CacheKeyResolver.resolveStaticKey(cacheout);
        this.cacheLevels = cacheLevels;
        this.method = method;
        this.targetClass = targetClass;
        this.cacheout = cacheout;
        this.conditionEvaluator = conditionEvaluator;
    }

    @Override
    public Object invoke(@NotNull MethodInvocation invocation) throws Throwable {
        if (ArrayUtils.isEmpty(cacheLevels))
            return invocation.proceed();

        // cache key
        String resolverKey = CacheKeyResolver.resolverExpl(
                staticKey, invocation.getArguments(), method, cacheout.delimiter());

        // pre-clear
        if (cacheout.beforeClean())
            evict(resolverKey);

        Object result = invocation.proceed();

        // post-clear
        afterEvict(resolverKey, invocation, result);
        return result;
    }

    private void evict(String resolverKey) {
        // for redis
        boolean isRedis =
                Arrays.stream(cacheLevels)
                        .anyMatch(v -> CacheLevel.REDIS == v);

        if(isRedis) {
            if (cacheout.pattern()) {
                CacheUtils.clearByPattern(resolverKey);
                return;
            }

            CacheUtils.clear(resolverKey);
            return;
        }

        // for local
        boolean isLocal =
                Arrays.stream(cacheLevels)
                        .anyMatch(v -> CacheLevel.LOCAL == v);
        if(isLocal)
            this.cacheable.removeCache(resolverKey);
    }

    private void afterEvict(String resolverKey, MethodInvocation invocation, Object result) {
        try {
            boolean spelResult = conditionEvaluator.matches(cacheout.condition(), invocation, targetClass, method, result);
            if (!spelResult)
                return;

        } catch (Exception e) {
            log.warn("Cacheout condition eval failed, condition: {}", cacheout.condition(), e);
        }

        Runnable action = () -> {
            try {
                evict(resolverKey);
            } catch (Exception e) {
                log.error("Cacheout failed, key: {}", resolverKey, e);
            }
        };

        // Transaction started
        if (cacheout.afterCommit()) {
            try {
                TransactionUtils.afterCommit(action);
                return;
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }

        // 兜底
        action.run();
    }
}
