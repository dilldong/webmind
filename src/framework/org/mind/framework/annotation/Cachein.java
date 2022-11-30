package org.mind.framework.annotation;

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
 * @author dp
 * @version 1.0
 * @date 2022/9/4
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cachein {

    String prefix() default "";

    String suffix() default "";

    String delimiter() default "_";

    String cacheable() default "";

    Cloneable.CloneType strategy() default Cloneable.CloneType.ORIGINAL;

    long expire() default 0L;

    TimeUnit unit() default TimeUnit.MILLISECONDS;

    boolean inRedis() default false;

    // When inRedis=true, should specify the returned java type
    Class<? extends Object>[] redisType() default {};
}
