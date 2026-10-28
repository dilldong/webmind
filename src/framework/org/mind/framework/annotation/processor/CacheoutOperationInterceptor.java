package org.mind.framework.annotation.processor;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;
import org.mind.framework.annotation.CacheLevel;
import org.mind.framework.annotation.Cacheout;
import org.mind.framework.cache.CacheUtils;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.util.TransactionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

/**
 *
 * @author Marcus
 * @version 1.0
 * @date 2026/10/24
 */
@Slf4j(topic = "Cacheout")
public class CacheoutOperationInterceptor implements MethodInterceptor {

    //    private final Set<Cacheable> cacheableSet;
//    private final CacheLevel[] defaultLevels;
//    private final List<Cacheout> cacheouts;
    private final Method method;
    private final Class<?> targetClass;
    private final Map<Cacheout, Object[]> cacheoutMap;

    private final ConditionEvaluator conditionEvaluator;

    public CacheoutOperationInterceptor(//Set<Cacheable> cacheableSet,
                                        //List<Cacheout> cacheouts,
                                        //CacheLevel[] defaultLevels,
                                        Map<Cacheout, Object[]> cacheoutMap,
                                        Method method,
                                        Class<?> targetClass,
                                        ConditionEvaluator conditionEvaluator) {
//        this.cacheableSet = cacheableSet;
//        this.defaultLevels = defaultLevels;
//        this.cacheouts = cacheouts;
        this.method = method;
        this.targetClass = targetClass;
        this.conditionEvaluator = conditionEvaluator;
        this.cacheoutMap = cacheoutMap;
    }

    @Override
    public Object invoke(@NotNull MethodInvocation invocation) throws Throwable {
        if (cacheoutMap.isEmpty())
            return invocation.proceed();

        // beforeClean 不同规则分开处理
        cacheoutMap.forEach((cacheout, values) -> {
            if (cacheout.beforeClean() && ArrayUtils.isNotEmpty((Object[]) values[1])) {
                String resolverKey = CacheKeyResolver.resolverExpl(
                        CacheKeyResolver.resolveStaticKey(cacheout),
                        invocation.getArguments(),
                        method,
                        cacheout.delimiter());

                evict(resolverKey, cacheout, (CacheLevel[]) values[1], (Cacheable) values[0]);
            }
        });

        Object result = invocation.proceed();

        // after
        cacheoutMap.forEach((cacheout, values) -> afterEvict(cacheout, values, invocation, result));

        return result;
    }

    private void evict(String resolverKey, Cacheout cacheout, CacheLevel[] levels, Cacheable cacheable) {
        // for redis
        boolean isRedis =
                Arrays.stream(levels)
                        .anyMatch(v -> CacheLevel.REDIS == v);

        if (isRedis) {
            if (cacheout.pattern()) {
                CacheUtils.clearByPattern(resolverKey);
                return;
            }

            CacheUtils.clear(resolverKey);
            return;
        }

        // for local
        boolean isLocal =
                Arrays.stream(levels)
                        .anyMatch(v -> CacheLevel.LOCAL == v);
        if (isLocal) {
            if (cacheout.pattern()) {
                cacheable.removeCacheContains(Strings.CS.replace(resolverKey, "*", StringUtils.EMPTY));
                return;
            }

            cacheable.removeCache(resolverKey);
        }
    }

    private void afterEvict(Cacheout cacheout, Object[] values, MethodInvocation invocation, Object result) {
        try {
            boolean spelResult = conditionEvaluator.matches(cacheout.condition(), invocation, targetClass, method, result);
            if (!spelResult)
                return;

        } catch (Exception e) {
            log.warn("Cacheout condition eval failed, condition: {}", cacheout.condition(), e);
        }

        Runnable action = () -> {
            String resolverKey = CacheKeyResolver.resolverExpl(
                    CacheKeyResolver.resolveStaticKey(cacheout),
                    invocation.getArguments(),
                    method,
                    cacheout.delimiter());

            try {
                evict(resolverKey, cacheout, (CacheLevel[]) values[1], (Cacheable) values[0]);
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
