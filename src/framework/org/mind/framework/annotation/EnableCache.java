package org.mind.framework.annotation;

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
 * @author dp
 * @version 1.0
 * @date 2022/9/4
 */
@Import({EanbleCacheConfiguration.class})
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@EnableAspectJAutoProxy(proxyTargetClass = false)
@Documented
public @interface EnableCache {
    /**
     * Default force cglib proxy.
     * JDK dynamic proxy: when the proxyTargetClass is false and the proxy target implements the interface.
     * CGLIB dynamic proxy: when the proxy target doesn't implement the interface, whether proxyTargetClass is true or false.
     * @return
     */
    boolean proxyTargetClass() default true;

}