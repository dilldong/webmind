package org.mind.framework.annotation.processor;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;
import org.mind.framework.annotation.CacheLevel;
import org.mind.framework.annotation.Cachein;
import org.mind.framework.annotation.Cacheout;
import org.mind.framework.annotation.Cacheouts;
import org.mind.framework.cache.CacheEventHandler;
import org.mind.framework.cache.CacheEventPublisher;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.cache.DefaultCacheEventPublisher;
import org.mind.framework.util.IOUtils;
import org.mind.framework.web.container.spring.SpringContainerAware;
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interceptor that parses the cache metadata on the method it is invoking and delegates
 * to an appropriate CacheinOperationsInterceptor or CacheoutOperationInterceptor.
 *
 * @author Marcus
 * @version 1.1
 * @date 2022/9/5
 */
@Slf4j
public class CacheAnnotationAwareInterceptor implements IntroductionInterceptor {
    /**
     * 没有 <code>@Cachein</code> <code>@Cacheout</code> 注释的缓存方法的哨兵值,
     * 防止反复反射扫描
     */
    private static final MethodInterceptor NULL_INTERCEPTOR = invocation -> {
        throw new UnsupportedOperationException("NULL_INTERCEPTOR sentinel should never be invoked directly");
    };

    private final BeanFactory beanFactory;

    private final Map<String, Map<Method, MethodInterceptor>> delegates;

    private final Cacheable defaultCache;

    private final CacheLevel[] defaultLevels;

    private final String cacheSyncName;

    private volatile CacheEventPublisher cacheEventPublisher;

    private final ConditionEvaluator conditionEvaluator;

    public CacheAnnotationAwareInterceptor(Cacheable defaultCache,
                                           CacheLevel[] defaultLevels,
                                           String cacheSyncName,
                                           BeanFactory beanFactory) {
        this.defaultCache = defaultCache;
        this.defaultLevels = defaultLevels;
        this.cacheSyncName = cacheSyncName;
        this.delegates = new ConcurrentReferenceHashMap<>(128);
        this.beanFactory = beanFactory;

        // 缓存同步器
        this.cacheEventPublisher =
                this.enableRedis(defaultLevels) ? buildPublisher() : CacheEventPublisher.NO_PUBLISHER;

        // SpEl 评估器
        this.conditionEvaluator = this.buildConditionEvaluator();
    }

    @Override
    public boolean implementsInterface(@NotNull Class<?> clazz) {
        return Cachein.class.isAssignableFrom(clazz)
                || Cacheout.class.isAssignableFrom(clazz)
                || Cacheouts.class.isAssignableFrom(clazz);
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 当目标对象本身又被别的代理包装时, 能拿到真实类而不是代理类。
        final Class<?> targetClass = AopProxyUtils.ultimateTargetClass(invocation.getThis());

        // 获取实际的方法
        Method method = AopUtils.getMostSpecificMethod(invocation.getMethod(), targetClass);

        Map<Method, MethodInterceptor> cachedMethods =
                this.delegates.computeIfAbsent(targetClass.getName(), k -> new ConcurrentHashMap<>(16));

        MethodInterceptor delegate = cachedMethods.computeIfAbsent(method, m -> {
            // 读写分开，禁止组合
            Cachein cachein = SpringContainerAware.resolveAnnotation(targetClass, m, Cachein.class);
            List<Cacheout> cacheouts = SpringContainerAware.parseCollectAnnotation(targetClass, m, Cacheout.class);

            validateRules(cacheouts);
            boolean hasIn = cachein != null;

            if (hasIn && CollectionUtils.isNotEmpty(cacheouts)) {
                String name = Strings.CS.replace(
                        StringUtils.substringAfterLast(m.toString(), StringUtils.SPACE),
                        targetClass.getPackage().getName() + IOUtils.DOT_SEPARATOR,
                        StringUtils.EMPTY);

                throw new IllegalStateException("@Cachein and @Cacheout must not be combined on " + name);
            }

            return hasIn ? getCacheinInterceptor(cachein, m) : getCacheoutInterceptor(cacheouts, m, targetClass);
        });

        return delegate == NULL_INTERCEPTOR ? invocation.proceed() : delegate.invoke(invocation);
    }


    private void validateRules(List<Cacheout> rules) {
        for (Cacheout rule : rules) {
            if (rule.beforeClean() && rule.condition().contains("#result")) {
                throw new IllegalStateException("#result is unavailable when beforeClean=true");
            }
        }
    }

    private MethodInterceptor getCacheinInterceptor(Cachein cachein, Method method) {
        if (cachein == null)
            return NULL_INTERCEPTOR;

        Cacheable cacheable =
                StringUtils.isEmpty(cachein.cacheable()) ?
                        defaultCache : this.beanFactory.getBean(cachein.cacheable(), Cacheable.class);

        CacheLevel[] levels = ArrayUtils.isEmpty(cachein.levels()) ? defaultLevels : cachein.levels();
        this.enableAndRegisterLocalCache(levels, cacheable);

        return new CacheinOperationInterceptor(
                cacheable,
                cachein,
                levels,
                cacheEventPublisher,
                method);
    }

    private MethodInterceptor getCacheoutInterceptor(List<Cacheout> cacheouts, Method method, Class<?> targetClass) {
        if (CollectionUtils.isEmpty(cacheouts))
            return NULL_INTERCEPTOR;

        Map<Cacheout, Object[]> cacheoutMap = new HashMap<>(cacheouts.size());
        for (Cacheout cacheout : cacheouts) {
            Cacheable cacheable =
                    StringUtils.isEmpty(cacheout.cacheable()) ?
                            defaultCache : this.beanFactory.getBean(cacheout.cacheable(), Cacheable.class);

            CacheLevel[] levels = ArrayUtils.isEmpty(cacheout.levels()) ? defaultLevels : cacheout.levels();
            this.enableAndRegisterLocalCache(levels, cacheable);
            cacheoutMap.put(cacheout, new Object[]{cacheable, levels});
        }

        return new CacheoutOperationInterceptor(
                cacheoutMap,
                method,
                targetClass,
                conditionEvaluator);
    }


    private void enableAndRegisterLocalCache(CacheLevel[] levels, Cacheable cacheable) {
        if (this.enableRedis(levels)) {
            // 防止未初始化, 构造函数是针对全局配置, 而在方法上则允许启用 Redis Level
            this.initCacheEventPublisher();

            // 注册本地 cacheable，供事件回调清除使用
            cacheEventPublisher.registerCacheable(cacheable);
        }
    }

    private void initCacheEventPublisher() {
        if (cacheEventPublisher != CacheEventPublisher.NO_PUBLISHER)
            return;

        synchronized (this) {
            if (cacheEventPublisher == CacheEventPublisher.NO_PUBLISHER) {
                cacheEventPublisher = buildPublisher();
            }
        }
    }

    private boolean enableRedis(CacheLevel[] cacheLevels) {
        return Arrays.stream(cacheLevels).anyMatch(v -> CacheLevel.REDIS == v);
    }

    // 自定义缓存事件发布器
    private CacheEventPublisher buildPublisher() {
        try {
            return this.beanFactory.getBean(CacheEventPublisher.BEAN_NAME, CacheEventPublisher.class);
        } catch (NoSuchBeanDefinitionException ignored) {
        }

        // 自定义缓存事件接收器
        CacheEventHandler cacheEventHandler = null;
        try {
            cacheEventHandler = this.beanFactory.getBean(CacheEventHandler.BEAN_NAME, CacheEventHandler.class);
        } catch (NoSuchBeanDefinitionException ignored) {
        }

        return new DefaultCacheEventPublisher(cacheEventHandler, cacheSyncName);
    }

    // 自定义 SpEl 评估器
    private ConditionEvaluator buildConditionEvaluator() {
        try {
            return this.beanFactory.getBean(ConditionEvaluator.BEAN_NAME, ConditionEvaluator.class);
        } catch (NoSuchBeanDefinitionException ignored) {
        }

        return new ConditionEvaluator();
    }

}