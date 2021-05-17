package org.mind.framework.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Intercept the execution of a handler.
 *
 * @author dp
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Interceptor {

    String[] value() default {"/*"};

    String[] excludes() default {};

    /*
     * forward sorting
     */
    int order() default 0;
}
