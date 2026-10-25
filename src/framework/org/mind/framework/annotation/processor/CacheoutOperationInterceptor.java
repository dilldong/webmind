package org.mind.framework.annotation.processor;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.ArrayUtils;
import org.jspecify.annotations.NonNull;
import org.mind.framework.annotation.CacheLevel;
import org.mind.framework.annotation.Cacheout;
import org.mind.framework.cache.CacheUtils;
import org.mind.framework.cache.Cacheable;

import java.lang.reflect.Method;

/**
 *
 * @author Marcus
 * @version 1.0
 * @date 2026/10/24
 */
@Slf4j(topic = "Cacheout")
public class CacheoutOperationInterceptor implements MethodInterceptor {

    //private final SpelEvaluator spel;
    private final Cacheable cacheable;
    private final CacheLevel[] cacheLevels;
    private final Method method;
    private final Cacheout cacheout;

    private final String staticKey;

    public CacheoutOperationInterceptor(Cacheable cacheable,
                                        Cacheout cacheout,
                                        CacheLevel[] cacheLevels,
                                        Method method) {
        this.staticKey = CacheKeyResolver.resolveStaticKey(cacheout);
        this.cacheable = cacheable;
        this.cacheLevels = cacheLevels;
        this.method = method;
        this.cacheout = cacheout;
    }

    @Override
    public Object invoke(@NonNull MethodInvocation invocation) throws Throwable {
        if (ArrayUtils.isEmpty(cacheLevels))
            return invocation.proceed();

        // cache key
        String resolverKey = CacheKeyResolver.resolverExpl(
                this.staticKey, invocation.getArguments(), this.method, cacheout.delimiter());

        // pre-clear
        if (cacheout.before())
            beforeEvict(invocation, resolverKey);

        Object result = invocation.proceed();

        // post-clear
        afterEvict(invocation, result);
        return result;
    }

    private void beforeEvict(MethodInvocation invocation, String resolverKey){
        CacheUtils.clearAsync(resolverKey);
    }

    private void afterEvict(MethodInvocation invocation, Object result) {
//        EvaluationContext ctx = spel.context(invocation, result);
//        if (!cacheout.condition().isEmpty() && !spel.evalBoolean(cacheout.condition(), ctx))
//            return;
//
//        Runnable action = () -> {
//            try {
//                Collection<String> ks = keys.resolve(cacheName, cacheout.key(), ctx);
//                evictor.evict(cacheName, ks, cacheout.broadcast());
//            } catch (Exception e) {
//                // 清除失败不能影响业务, 但须被发现: 缓存会脏到 TTL 过期
//                log.error("Cacheout failed, key: {}", cacheout.key(), e);
//            }
//        };
//
//        if (cacheout.afterCommit() && TransactionSynchronizationManager.isSynchronizationActive()) {
//            TransactionSynchronizationManager.registerSynchronization(
//                    new TransactionSynchronization() {
//                        @Override
//                        public void afterCommit() { action.run(); }
//                    });
//        } else {
//            action.run();
//        }
    }
}
