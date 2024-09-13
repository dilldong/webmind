package org.mind.framework.annotation;

import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Action method annotation for mapping URI
 *
 * @author dp
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Mapping {

    String value() default "/";

    RequestMethod[] method() default {};

    /**
     * Enable request logging
     */
    boolean requestLog() default true;

    /**
     * Simple one line logging
     */
    boolean simpleLogging() default false;

    /**
     * Clear the returned collection object
     */
    boolean clearResult() default false;
}
