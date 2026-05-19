package org.mind.framework.annotation.processor;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.mind.framework.annotation.CacheLevel;
import org.mind.framework.annotation.Cachein;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.util.DateUtils;
import org.mind.framework.util.MatcherUtils;
import org.redisson.api.RMapCache;
import org.redisson.api.map.event.EntryCreatedListener;
import org.redisson.api.map.event.EntryExpiredListener;
import org.redisson.api.map.event.EntryRemovedListener;
import org.redisson.api.map.event.EntryUpdatedListener;
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Interceptor that parses the cachein metadata on the method it is invoking and delegates
 * to an appropriate CacheinOperationsInterceptor.
 *
 * @author Marcus
 * @version 1.0
 * @date 2022/9/5
 */
@Slf4j
public class CacheinAnnotationAwareInterceptor implements IntroductionInterceptor, CacheEventPublisher {
    /**
     * 没有 @Cachein 注释的缓存方法的哨兵值
     * 防止反复反射扫描
     */
    private static final MethodInterceptor NULL_INTERCEPTOR = invocation -> {
        throw new UnsupportedOperationException("NULL_INTERCEPTOR sentinel should never be invoked directly");
    };

    private final BeanFactory beanFactory;

    private final Map<String, Map<Method, MethodInterceptor>> delegates;

    /**
     * Cacheable → staticKey 列表。
     * 同一 Cacheable 对象引用可能对应多个不同的 staticKey
     */
    private final Map<Cacheable, Set<String>> cacheableRegistry;

    private final Cacheable defaultCache;

    private final CacheLevel[] defaultLevels;

    private volatile RMapCache<String, String> cacheEventListener;

    private CacheEventHandler cacheEventHandler;

    public CacheinAnnotationAwareInterceptor(Cacheable defaultCache,
                                             CacheLevel[] defaultLevels,
                                             BeanFactory beanFactory) {
        this.defaultCache = defaultCache;
        this.defaultLevels = defaultLevels;
        this.delegates = new ConcurrentReferenceHashMap<>();
        this.beanFactory = beanFactory;

        // IdentityHashMap 用 == 而非 equals 判重, 不依赖 equals/hashCode 的实现
        this.cacheableRegistry = Collections.synchronizedMap(new IdentityHashMap<>());

        if (this.enableRedis(defaultLevels))
            this.initCacheEventListener();

        // 自定义缓存事件接收器
        try {
            this.cacheEventHandler = this.beanFactory.getBean(CacheEventHandler.BEAN_NAME, CacheEventHandler.class);
        } catch (NoSuchBeanDefinitionException e) {
            this.cacheEventHandler = null;
        }
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

    @Override
    public void publish(String key, long expire, TimeUnit unit) {
        // Redis level not active for this interceptor — nothing to publish
        if (Objects.isNull(cacheEventListener))
            return;

        if(expire == 0L) {
            cacheEventListener.fastPutAsync(key, Instant.MAX.toString());
            return;
        }

        long delay = unit.toMillis(expire) - 20L;
        cacheEventListener.fastPutAsync(
                key,
                Instant.ofEpochMilli(DateUtils.CachedTime.currentMillis() + delay).toString(),
                delay,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void registerCacheable(String key, Cacheable cacheable) {
        Set<String> result = cacheableRegistry.computeIfAbsent(cacheable, keys -> ConcurrentHashMap.newKeySet());

        // 拆解 key 前缀
        if (MatcherUtils.checkCount(key, MatcherUtils.PARAM_MATCH_PATTERN) > 0) {
            String prefix = StringUtils.substringBefore(key, "#{");
            result.add(prefix);
            return;
        }

        result.add(key);
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
        CacheEventPublisher eventPublisher = CacheEventPublisher.NO_PUBLISHER;

        // 计算静态 key
        String staticKey = resolveStaticKey(cachein);

        if (this.enableRedis(levels)) {
            // init sync key listener
            this.initCacheEventListener();

            // 注册 prefix → cacheable 映射，供事件回调使用
            this.registerCacheable(staticKey, cacheable);

            eventPublisher = this;
        }

        return new CacheinOperationInterceptor(
                cacheable,
                cachein,
                levels,
                eventPublisher,
                staticKey);
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

    private void initCacheEventListener() {
        if (Objects.nonNull(this.cacheEventListener))
            return;

        synchronized (this) {
            if (Objects.isNull(this.cacheEventListener)) {
                RMapCache<String, String> eventListener = RedissonHelper.getClient().getMapCache(KEY_EVENT_MAPCACHE);

                eventListener.addListener((EntryRemovedListener<String, String>) event -> {
                    log.debug("Entry removed, key: {}, expire: {}", event.getKey(), event.getValue());
                    evictLocalCache(event.getKey());

                    if (Objects.nonNull(cacheEventHandler))
                        cacheEventHandler.onRemoved(event.getKey());
                });

                eventListener.addListener((EntryExpiredListener<String, String>) event -> {
                    log.debug("Entry expired, key: {}, expire: {}", event.getKey(), event.getValue());
                    evictLocalCache(event.getKey());

                    if (Objects.nonNull(cacheEventHandler))
                        cacheEventHandler.onExpired(event.getKey());
                });

                eventListener.addListener((EntryUpdatedListener<String, String>) event -> {
                    log.debug("Entry updated, key: {}, expire: {}", event.getKey(), event.getValue());
                    evictLocalCache(event.getKey());

                    if (Objects.nonNull(cacheEventHandler))
                        cacheEventHandler.onUpdated(event.getKey());
                });

                eventListener.addListener((EntryCreatedListener<String, String>) event -> {
                    log.debug("Entry created, key: {}, expire: {}", event.getKey(), event.getValue());

                    if (Objects.nonNull(cacheEventHandler))
                        cacheEventHandler.onCreated(event.getKey());
                });

                // 安全发布 (safe publication) 模式
                // 所有 listener 注册完毕后，再发布给其他线程
                this.cacheEventListener = eventListener;
            }
        }
    }

    /**
     * 根据完整 key 匹配注册表中的前缀，定向清除对应 Cacheable 的本地缓存。
     * 匹配规则：完整 key 以注册的前缀开头（兼容静态key和带动态参数的key）。
     */
    private void evictLocalCache(String key) {
        cacheableRegistry.forEach((cacheable, staticKeys) -> {
            boolean matched = staticKeys.stream().anyMatch(key::startsWith);

            if (matched) {
                cacheable.removeCache(key);
                log.debug("Evicted local cache key: {}", key);
            }
        });
    }
}
