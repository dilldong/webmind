package org.mind.framework.annotation;

import org.apache.commons.lang3.StringUtils;
import org.mind.framework.cache.AbstractCache;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to evict cache
 *
 * @author Marcus
 * @version 1.0
 * @date 2026/10/24
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Cacheout {

    String prefix() default StringUtils.EMPTY;

    String suffix() default StringUtils.EMPTY;

    String delimiter() default AbstractCache.CACHE_DELIMITER;

    String cacheable() default StringUtils.EMPTY;

    String condition() default StringUtils.EMPTY;

    /**
     * 使用 Pattern 模式匹配清除
     */
    boolean pattern() default false;

    /**
     * true: 方法执行前清除 (异常也会清)
     */
    boolean beforeClean() default false;

    /**
     * 有事务时: 提交后再清除, 否则直接清除
     */
    boolean afterCommit() default true;

    /**
     * 清理的缓存目标, 默认: Redis & Local
     */
    CacheLevel[] levels() default {};

}
