package org.mind.framework.annotation;

import org.apache.commons.lang3.StringUtils;
import org.mind.framework.cache.AbstractCache;
import org.mind.framework.service.Cloneable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Annotation-based cache reads and writes,
 * Dynamic proxy implementation.
 *
 * @author Marcus
 * @version 1.0
 * @date 2022/9/4
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cachein {

    String prefix() default StringUtils.EMPTY;

    String suffix() default StringUtils.EMPTY;

    String delimiter() default AbstractCache.CACHE_DELIMITER;

    String cacheable() default StringUtils.EMPTY;

    Cloneable.CloneType strategy() default Cloneable.CloneType.ORIGINAL;

    long expire() default 0L;

    TimeUnit unit() default TimeUnit.MILLISECONDS;

    boolean inRedis() default false;

    // When inRedis=true, should specify the returned java type
    Class<?>[] redisType() default {};

    // When set true, the object in the cache is returned first, even if it is empty.
    boolean penetration() default true;
}
