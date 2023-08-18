package org.mind.framework.annotation;

import org.aopalliance.aop.Advice;
import org.jetbrains.annotations.NotNull;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.util.ReflectionUtils;
import org.mind.framework.web.interceptor.CacheinAnnotationAwareInterceptor;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.aop.support.annotation.AnnotationClassFilter;
import org.springframework.aop.support.annotation.AnnotationMethodMatcher;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.OrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ObjectUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Basic configuration for <code>@Cachein</code> processing.
 *
 * @version 1.0
 * @auther Marcus
 * @date 2022/9/5
 */
//@Component
//@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class EanbleCacheConfiguration extends AbstractPointcutAdvisor implements IntroductionAdvisor, BeanFactoryAware, InitializingBean, SmartInitializingSingleton {
    public static final String ATTR_BEAN_NAME = "org.mind.framework.annotation.EanbleCacheConfiguration";

    private Pointcut pointcut;
    private Advice advice;
    private Cacheable cacheable;
    private BeanFactory beanFactory;

    @Override
    public ClassFilter getClassFilter() {
        return this.pointcut.getClassFilter();
    }

    @Override
    public void validateInterfaces() throws IllegalArgumentException {
    }

    @Override
    public Class<?>[] getInterfaces() {
        return new Class[]{org.mind.framework.cache.Cacheable.class};
    }

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public Advice getAdvice() {
        return advice;
    }

    @Override
    public void setBeanFactory(@NotNull BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.cacheable = this.findBean(Cacheable.class);
//        Set<Class<? extends Annotation>> cacheinAnnotationTypes = new LinkedHashSet<>(1);
//        cacheinAnnotationTypes.add(Cachein.class);
//        this.pointcut = buildPointcut(cacheinAnnotationTypes);
        this.pointcut = buildPointcut(Cachein.class);
        this.advice = buildAdvice();
        if (this.advice instanceof BeanFactoryAware)
            ((BeanFactoryAware) advice).setBeanFactory(beanFactory);
    }

    @Override
    public void afterSingletonsInstantiated() {
    }

    private <T> List<T> findBeans(Class<? extends T> type) {
        if (this.beanFactory instanceof ListableBeanFactory) {
            ListableBeanFactory listable = (ListableBeanFactory) this.beanFactory;
            if (listable.getBeanNamesForType(type).length > 0) {
                List<T> list = new ArrayList<>(listable.getBeansOfType(type, false, false).values());
                OrderComparator.sort(list);
                return list;
            }
        }

        return null;
    }

    private <T> T findBean(Class<? extends T> type) {
        if (this.beanFactory instanceof ListableBeanFactory) {
            ListableBeanFactory listable = (ListableBeanFactory) this.beanFactory;
            if (listable.getBeanNamesForType(type, false, false).length == 1)
                return listable.getBean(type);
        }

        return null;
    }

    /**
     * Calculate a pointcut for the given cachein annotation types, if any.
     *
     * @param cacheinAnnotationTypes the cachein annotation types to introspect
     * @return the applicable Pointcut object, or {@code null} if none
     */
    private Pointcut buildPointcut(Set<Class<? extends Annotation>> cacheinAnnotationTypes) {
        ComposablePointcut result = null;

        for (Class<? extends Annotation> clazzType : cacheinAnnotationTypes) {
            Pointcut filter = new AnnotationClassOrMethodPointcut(clazzType);
            if (Objects.isNull(result)) {
                result = new ComposablePointcut(filter);
                continue;
            }
            result.union(filter);
        }

        return result;
    }

    private Pointcut buildPointcut(Class<? extends Annotation> cacheinAnnotationType) {
        Pointcut filter = new AnnotationClassOrMethodPointcut(cacheinAnnotationType);
        return new ComposablePointcut(filter);
    }

    private CacheinAnnotationAwareInterceptor buildAdvice() {
        CacheinAnnotationAwareInterceptor interceptor = new CacheinAnnotationAwareInterceptor();
        interceptor.setDefaultCache(cacheable);
        return interceptor;
    }


    private static class AnnotationClassOrMethodPointcut extends StaticMethodMatcherPointcut {
        private final MethodMatcher methodResolver;

        AnnotationClassOrMethodPointcut(Class<? extends Annotation> annotationType) {
            this.methodResolver = new AnnotationMethodMatcher(annotationType);
            setClassFilter(new AnnotationClassOrMethodFilter(annotationType));
        }

        @Override
        public boolean matches(@NotNull Method method, @NotNull Class<?> targetClass) {
            return getClassFilter().matches(targetClass) || this.methodResolver.matches(method, targetClass);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;

            if (!(other instanceof AnnotationClassOrMethodPointcut))
                return false;

            AnnotationClassOrMethodPointcut otherAdvisor = (AnnotationClassOrMethodPointcut) other;
            return ObjectUtils.nullSafeEquals(this.methodResolver, otherAdvisor.methodResolver);
        }
    }

    private static class AnnotationClassOrMethodFilter extends AnnotationClassFilter {
        private final AnnotationMethodsResolver methodResolver;

        AnnotationClassOrMethodFilter(Class<? extends Annotation> annotationType) {
            super(annotationType, true);
            this.methodResolver = new AnnotationMethodsResolver(annotationType);
        }

        @Override
        public boolean matches(@NotNull Class<?> clazz) {
            return super.matches(clazz) || this.methodResolver.hasAnnotatedMethods(clazz);
        }
    }

    private static class AnnotationMethodsResolver {
        private final Class<? extends Annotation> annotationType;

        public AnnotationMethodsResolver(Class<? extends Annotation> annotationType) {
            this.annotationType = annotationType;
        }

        public boolean hasAnnotatedMethods(Class<?> clazz) {
            final AtomicBoolean found = new AtomicBoolean(false);
            ReflectionUtils.doWithMethods(clazz, method -> {
                if (found.get())
                    return;

                Annotation annotation = AnnotationUtils.findAnnotation(
                        method,
                        AnnotationMethodsResolver.this.annotationType);
                if (Objects.nonNull(annotation))
                    found.set(true);
            });
            return found.get();
        }
    }
}
