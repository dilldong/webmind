package org.mind.framework.annotation;

import javax.servlet.DispatcherType;
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
public @interface Filter {

    String[] value() default {"/*"};

    boolean matchAfter() default true;

    DispatcherType[] dispatcherTypes() default {DispatcherType.REQUEST};

    /*
     * filter sorting
     */
    int order() default 0;
}
