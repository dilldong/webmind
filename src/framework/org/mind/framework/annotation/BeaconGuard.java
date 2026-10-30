package org.mind.framework.annotation;

import org.mind.framework.cache.CacheUtils;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author: Marcus
 * @date: 2026/8/19
 * @version: 1.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface BeaconGuard {

    boolean exclusive() default false;

    long waitTime() default CacheUtils.WAIT_TIME;

    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
