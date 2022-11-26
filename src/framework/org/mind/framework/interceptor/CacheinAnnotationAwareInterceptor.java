package org.mind.framework.interceptor;

import lombok.Setter;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.annotation.Cachein;
import org.mind.framework.cache.Cacheable;
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interceptor that parses the cachein metadata on the method it is invoking and delegates
 * to an appropriate CacheinOperationsInterceptor.
 *
 * @version 1.0
 * @auther Marcus
 * @date 2022/9/5
 */
public class CacheinAnnotationAwareInterceptor implements IntroductionInterceptor, BeanFactoryAware {
    private final Map<Object, Map<Method, MethodInterceptor>> delegates = new ConcurrentReferenceHashMap<>();
    private BeanFactory beanFactory;
    @Setter
    private Cacheable defaultCache;

    @Override
    public boolean implementsInterface(Class<?> clazz) {
        return Cacheable.class.isAssignableFrom(clazz);
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object target = invocation.getThis();
        Map<Method, MethodInterceptor> cachedMethods = this.delegates.get(target);
        if (Objects.isNull(cachedMethods)) {
            cachedMethods = new ConcurrentHashMap<>();
            this.delegates.putIfAbsent(target, cachedMethods);
        }

        Method method = invocation.getMethod();
        MethodInterceptor delegate = cachedMethods.get(method);
        if (Objects.isNull(delegate)) {
            Cachein cachein = AnnotatedElementUtils.findMergedAnnotation(method, Cachein.class);

            if (Objects.isNull(cachein)) {
                cachein = classLevelAnnotation(method, Cachein.class);
            }

            if (Objects.isNull(cachein)) {
                cachein = findAnnotationOnTarget(target, method, Cachein.class);
            }

            if (Objects.nonNull(cachein)) {
                delegate = this.getDefaultInterceptor(invocation.getArguments(), method, cachein);

                // Customizable implementation of interceptor
//                if (StringUtils.isNotEmpty(cachein.interceptor()))
//                    delegate = this.beanFactory.getBean(cachein.interceptor(), MethodInterceptor.class);

                if (Objects.nonNull(delegate))
                    cachedMethods.putIfAbsent(method, delegate);
            }
        }
        return Objects.nonNull(delegate) ? delegate.invoke(invocation) : invocation.proceed();
    }

    private <V extends Annotation> V findAnnotationOnTarget(Object target, Method method, Class<V> annotation) {
        try {
            Method targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
            V cachein = AnnotatedElementUtils.findMergedAnnotation(targetMethod, annotation);
            return Objects.isNull(cachein) ? classLevelAnnotation(targetMethod, annotation) : cachein;
        } catch (Exception e) {
            return null;
        }
    }

    /*
     * With a class level annotation, exclude @Recover methods.
     */
    private <V extends Annotation> V classLevelAnnotation(Method method, Class<V> annotation) {
        return AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), annotation);
    }

    private MethodInterceptor getDefaultInterceptor(Object[] params, Method method, Cachein cachein) {
        Cacheable cacheable = defaultCache;

        if (StringUtils.isNotEmpty(cachein.cacheable()))
            cacheable = this.beanFactory.getBean(cachein.cacheable(), Cacheable.class);

        CacheinOperationInterceptor opInterceptor =
                new CacheinOperationInterceptor(
                        cacheable,
                        cachein.strategy(),
                        cachein.expire(),
                        cachein.unit(),
                        cachein.inRedis(),
                        cachein.redisType()[0]);

        boolean isPrefix = StringUtils.isNotEmpty(cachein.prefix());
        boolean isSuffix = StringUtils.isNotEmpty(cachein.suffix());

        if (isPrefix && isSuffix)
            opInterceptor.setKey(String.join(cachein.delimiter(), cachein.prefix(), cachein.suffix()));
        else if (isPrefix)
            opInterceptor.setKey(cachein.prefix());
        else if (isSuffix)
            opInterceptor.setKey(cachein.suffix());
        else
            throw new IllegalArgumentException("At least one of 'prefix' or 'suffix'.");

        return opInterceptor;
    }
}
