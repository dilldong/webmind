package org.mind.framework.annotation;

import org.mind.framework.service.Cloneable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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

    long expire() default -1;

//    @Cachein(prefix=,
//              suffix="${userId}",
//              delimiter="_",
//              cacheable = "cacheManager",
//              strategy = original || copy,
//              expire=1_800_000L)

//    prefix: key 的前缀
//    suffix: key 的后缀(动态属性)
//    delimiter: key的分隔符号, 默认为: _
//    cacheable: 实现了Cacheable接口的cache对象, 默认为: cacheManager
//    strategy: 获取策略, original: 原对象, copy: clone的对象, 需要自行实现Cloneable接口
//    expire: 过期时间(ms)
}
