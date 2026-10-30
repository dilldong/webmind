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
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotationPredicates;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.RepeatableContainers;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
            Cachein cachein = resolveAnnotation(targetClass, m, Cachein.class);
            List<Cacheout> cacheouts = parseCollectAnnotation(targetClass, m, Cacheout.class);

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

    private <V extends Annotation> V resolveAnnotation(Class<?> targetClass, Method method, Class<V> annotationType) {
        // 1. 接口 & 方法上的
        V annotation = AnnotatedElementUtils.findMergedAnnotation(method, annotationType);

        if (annotation != null)
            return annotation;

        // 2. 类级别上的
        annotation = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), annotationType);
        if (annotation != null)
            return annotation;

        // 3. 目标类和声明类不同(方法继承自父类)时, 再查目标类
        return targetClass != method.getDeclaringClass()
                ? AnnotatedElementUtils.findMergedAnnotation(targetClass, annotationType)
                : null;
    }

    private <V extends Annotation> List<V> parseCollectAnnotation(Class<?> targetClass, Method method, Class<V> annotationType) {
        // 1. 接口 & 方法上的
        List<V> list = resolveCollectAnnotation(method, annotationType);
        if (!list.isEmpty())
            return list;

        // 2. 类级别上的
        list = resolveCollectAnnotation(method.getDeclaringClass(), annotationType);
        if (!list.isEmpty())
            return list;

        // 3. 目标类和声明类不同(方法继承自父类)时, 再查目标类
        return targetClass != method.getDeclaringClass()
                ? resolveCollectAnnotation(targetClass, annotationType)
                : Collections.emptyList();
    }

    /**
     * 同时识别单个 @Cacheout 和被包进 @Cacheouts 的多个 @Cacheout。
     * 沿层级查找,只取最近一层: 实现方法上有注解时, 接口方法上的不再合并进来。
     */
    private <V extends Annotation> List<V> resolveCollectAnnotation(AnnotatedElement element, Class<V> annotationType) {
        return MergedAnnotations
                .from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.standardRepeatables())
                .stream(annotationType)
                .filter(MergedAnnotationPredicates.firstRunOf(MergedAnnotation::getAggregateIndex))
                .map(MergedAnnotation::synthesize)
                .collect(Collectors.toList());
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