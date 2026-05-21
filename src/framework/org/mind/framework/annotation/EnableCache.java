package org.mind.framework.annotation;

import org.mind.framework.annotation.processor.EnableCacheConfiguration;
import org.mind.framework.cache.CacheEventPublisher;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation-based cache reads and writes,
 * CGLIB dynamic proxy implementation.
 *
 * @author Marcus
 * @version 1.0
 * @date 2022/9/4
 */
@Import({EnableCacheConfiguration.class})
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@EnableAspectJAutoProxy
@Documented
public @interface EnableCache {
    /**
     * Default force cglib proxy.
     * <br/>1.JDK dynamic proxy: when the proxyTargetClass is false and the proxy target implements the interface.
     * <br/>2.CGLIB dynamic proxy: when the proxy target doesn't implement the interface, whether proxyTargetClass is true or false.
     * @return
     */
    boolean proxyTargetClass() default false;

    boolean exposeProxy() default false;

    CacheLevel[] levels() default {};

    /**
     * 用于 Redisson RMapCache 同步器的名称，
     * L2 -> L1 缓存基于 RMapCache 进行同步。
     */
    String cacheSyncName() default CacheEventPublisher.KEY_EVENT_MAPCACHE;
}