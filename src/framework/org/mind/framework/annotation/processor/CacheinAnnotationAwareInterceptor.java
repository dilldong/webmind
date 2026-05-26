package org.mind.framework.annotation.processor;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.mind.framework.annotation.CacheLevel;
import org.mind.framework.annotation.Cachein;
import org.mind.framework.cache.CacheEventHandler;
import org.mind.framework.cache.CacheEventPublisher;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.cache.DefaultCacheEventPublisher;
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interceptor that parses the cachein metadata on the method it is invoking and delegates
 * to an appropriate CacheinOperationsInterceptor.
 *
 * @author Marcus
 * @version 1.0
 * @date 2022/9/5
 */
@Slf4j
public class CacheinAnnotationAwareInterceptor implements IntroductionInterceptor {
    /**
     * 没有 @Cachein 注释的缓存方法的哨兵值
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

    private CacheEventHandler cacheEventHandler;

    public CacheinAnnotationAwareInterceptor(Cacheable defaultCache,
                                             CacheLevel[] defaultLevels,
                                             String cacheSyncName,
                                             BeanFactory beanFactory) {
        this.defaultCache = defaultCache;
        this.defaultLevels = defaultLevels;
        this.cacheSyncName = cacheSyncName;
        this.delegates = new ConcurrentReferenceHashMap<>();
        this.beanFactory = beanFactory;

        // 自定义缓存事件接收器
        try {
            cacheEventHandler = this.beanFactory.getBean(CacheEventHandler.BEAN_NAME, CacheEventHandler.class);
        } catch (NoSuchBeanDefinitionException ignored) {
            cacheEventHandler = null;
        }

        // 缓存同步器
        this.cacheEventPublisher =
                this.enableRedis(defaultLevels) ? buildPublisher() : CacheEventPublisher.NO_PUBLISHER;
    }

    @Override
    public boolean implementsInterface(@NotNull Class<?> clazz) {
        return Cacheable.class.isAssignableFrom(clazz);
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object target = Objects.requireNonNull(invocation.getThis());

        Map<Method, MethodInterceptor> cachedMethods =
                this.delegates.computeIfAbsent(target.getClass().getName(), k -> new ConcurrentHashMap<>(16));

        MethodInterceptor delegate = cachedMethods.computeIfAbsent(invocation.getMethod(), m -> {
            Cachein cachein = resolveCachein(target, m);
            return Objects.isNull(cachein) ? NULL_INTERCEPTOR : getDefaultInterceptor(cachein);
        });

        return delegate == NULL_INTERCEPTOR ? invocation.proceed() : delegate.invoke(invocation);
    }

    private Cachein resolveCachein(Object target, Method method) {
        // 1. 接口方法上的 @Cachein
        Cachein cachein = AnnotatedElementUtils.findMergedAnnotation(method, Cachein.class);

        // 2. 实现类方法上的 @Cachein，或实现类类级别的 @Cachein
        if (Objects.isNull(cachein))
            cachein = findAnnotationOnMethod(target, method);

        // 3. 接口类级别的 @Cachein（method 此时是接口 method）
        if (Objects.isNull(cachein))
            cachein = classLevelAnnotation(method, Cachein.class);

        return cachein;
    }

    private <V extends Annotation> V findAnnotationOnMethod(Object target, Method method) {
        try {
            Method targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
            V cachein = AnnotatedElementUtils.findMergedAnnotation(targetMethod, (Class<V>) Cachein.class);
            return Objects.isNull(cachein) ? classLevelAnnotation(targetMethod, (Class<V>) Cachein.class) : cachein;
        } catch (Exception e) {
            return null;
        }
    }

    /*
     * With a class level annotation, exclude @Recover methods.
     */
    private <V extends Annotation> V classLevelAnnotation(Method method, Class<V> annotation) {
        return AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), annotation);
    }

    private MethodInterceptor getDefaultInterceptor(Cachein cachein) {
        Cacheable cacheable =
                StringUtils.isEmpty(cachein.cacheable()) ?
                        defaultCache : this.beanFactory.getBean(cachein.cacheable(), Cacheable.class);

        CacheLevel[] levels = ArrayUtils.isEmpty(cachein.levels()) ? defaultLevels : cachein.levels();

        // 计算静态 key
        String staticKey = resolveStaticKey(cachein);

        if (this.enableRedis(levels)) {

            // 防止未初始化, 构造函数是针对全局配置, 而在方法上则允许启用 Redis Level
            this.initCacheEventPublisher();

            // 注册 prefix → cacheable 映射，供事件回调使用
            cacheEventPublisher.registerCacheable(staticKey, cacheable);
        }

        return new CacheinOperationInterceptor(
                cacheable,
                cachein,
                levels,
                cacheEventPublisher,
                staticKey);
    }

    private void initCacheEventPublisher(){
        if(cacheEventPublisher != CacheEventPublisher.NO_PUBLISHER)
            return;

        synchronized (this) {
            if(cacheEventPublisher == CacheEventPublisher.NO_PUBLISHER) {
                cacheEventPublisher = buildPublisher();
            }
        }
    }

    private String resolveStaticKey(Cachein cachein) {
        boolean isPrefix = StringUtils.isNotEmpty(cachein.prefix());
        boolean isSuffix = StringUtils.isNotEmpty(cachein.suffix());

        if (isPrefix && isSuffix)
            return String.join(cachein.delimiter(), cachein.prefix(), cachein.suffix());
        else if (isPrefix)
            return cachein.prefix();
        else if (isSuffix)
            return cachein.suffix();
        else
            throw new IllegalArgumentException("At least one of 'prefix' or 'suffix' must be specified");
    }

    private boolean enableRedis(CacheLevel[] cacheLevels) {
        return Arrays.stream(cacheLevels).anyMatch(v -> CacheLevel.REDIS == v);
    }

    // 自定义缓存事件发布器
    private CacheEventPublisher buildPublisher() {
        try {
            return this.beanFactory.getBean(CacheEventPublisher.BEAN_NAME, CacheEventPublisher.class);
        } catch (NoSuchBeanDefinitionException ignored) {
            return new DefaultCacheEventPublisher(cacheEventHandler, cacheSyncName);
        }
    }
}