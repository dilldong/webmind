package org.mind.framework.web.container.spring;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.mind.framework.ContextSupport;
import org.mind.framework.annotation.Interceptor;
import org.mind.framework.annotation.Mapping;
import org.mind.framework.exception.NotSupportedException;
import org.mind.framework.util.ReflectionUtils;
import org.mind.framework.web.container.ContainerAware;
import org.mind.framework.web.dispatcher.handler.Execution;
import org.mind.framework.web.dispatcher.support.Catcher;
import org.mind.framework.web.dispatcher.support.CatcherMapping;
import org.mind.framework.web.dispatcher.support.ConverterFactory;
import org.mind.framework.web.interceptor.CorsCatcher;
import org.mind.framework.web.interceptor.CorsInterceptor;
import org.mind.framework.web.interceptor.HandlerInterceptor;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotationPredicates;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.RepeatableContainers;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.cors.CorsConfiguration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Web Container Wrapper for Spring 2+
 *
 * @author dp
 */
@Slf4j(topic = "SpringContainer")
public class SpringContainerAware implements ContainerAware {

    @Getter
    private ServletConfig servletConfig;

    @Override
    public void init(ServletConfig config) {
        this.servletConfig = config;
        ContextSupport.initSpringByServlet(config.getServletContext());
    }

    @Override
    public ServletContext getServletContext() {
        return getServletConfig().getServletContext();
    }

    public static <V extends Annotation> V resolveAnnotation(Class<?> targetClass, Method method, Class<V> annotationType) {
        // 1. 接口 & 方法上的
        V annotation = AnnotatedElementUtils.findMergedAnnotation(method, annotationType);

        if (annotation != null)
            return annotation;

        // 2. 类级别上的
        annotation = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), annotationType);
        if (annotation != null)
            return annotation;

        // 3. 目标类和声明类不同(方法继承自父类)时, 再查目标类
        return targetClass != method.getDeclaringClass()
                ? AnnotatedElementUtils.findMergedAnnotation(targetClass, annotationType)
                : null;
    }

    public static <V extends Annotation> List<V> parseCollectAnnotation(Class<?> targetClass, Method method, Class<V> annotationType) {
        // 1. 接口 & 方法上的
        List<V> list = resolveCollectAnnotation(method, annotationType);
        if (!list.isEmpty())
            return list;

        // 2. 类级别上的
        list = resolveCollectAnnotation(method.getDeclaringClass(), annotationType);
        if (!list.isEmpty())
            return list;

        // 3. 目标类和声明类不同(方法继承自父类)时, 再查目标类
        return targetClass != method.getDeclaringClass()
                ? resolveCollectAnnotation(targetClass, annotationType)
                : Collections.emptyList();
    }

    /**
     * 同时识别单个 Annotation 和被包进 Annotations 的多个 Annotation。
     * 沿层级查找, 只取最近一层: 实现方法上有注解时, 接口方法上的不再合并进来。
     */
    public static <V extends Annotation> List<V> resolveCollectAnnotation(AnnotatedElement element, Class<V> annotationType) {
        return MergedAnnotations
                .from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.standardRepeatables())
                .stream(annotationType)
                .filter(MergedAnnotationPredicates.firstRunOf(MergedAnnotation::getAggregateIndex))
                .map(MergedAnnotation::synthesize)
                .collect(Collectors.toList());
    }


    @Override
    public List<Object> loadBeans(boolean... excludeSpringSelf) {
        String[] names = ContextSupport.getBeanNames();
        //get defined name by spring
        List<String> beanNames = (ArrayUtils.isEmpty(excludeSpringSelf) || !excludeSpringSelf[0]) ?
                Arrays.asList(names) :
                Arrays.stream(names)
                        .filter(name -> !Strings.CS.startsWith(name, "org.springframework"))
                        .collect(Collectors.toList());

        List<Object> beans = new ArrayList<>(beanNames.size());

        beanNames.forEach(name -> beans.add(ContextSupport.getBean(name)));
        return Collections.unmodifiableList(beans);
    }

    /**
     * Initialize All {@link Interceptor} bean objects.
     */
    @Override
    public void loadInterceptor(Consumer<Catcher> consumer) {
        Map<String, Object> interceptors = ContextSupport.getApplicationContext().getBeansWithAnnotation(Interceptor.class);

        for (Map.Entry<String, Object> entry : interceptors.entrySet()) {
            loadInterceptor(entry.getValue(), consumer);
        }
    }

    /**
     * Initialize all {@link Mapping} bean objects.
     */
    @Override
    public void loadMapping(BiConsumer<String, Execution> biConsumer) {
        StringJoiner joiner = new StringJoiner(", ");

        // Load all Controller Bean
        Map<String, Object> controllers = ContextSupport.getApplicationContext().getBeansWithAnnotation(Controller.class);

        for (Map.Entry<String, Object> entry : controllers.entrySet()) {
            loadMapping(entry.getValue(), (route, execution) -> {
                biConsumer.accept(route, execution);
                joiner.add(route);
            });
        }

        if (joiner.length() > 0)
            log.info("Loaded URI mapping: {}", joiner);
    }

    /**
     * Initialize All {@link CrossOrigin} bean objects.
     */
    @Override
    public void loadCorsOrigin(Consumer<Catcher> consumer) {
        // Load all CrossOrigin Bean
        Map<String, Object> crossOrigins = ContextSupport.getApplicationContext().getBeansWithAnnotation(Controller.class);

        for (Map.Entry<String, Object> entry : crossOrigins.entrySet()) {
            loadCrossOrigin(entry.getValue(), consumer);
        }
    }

    private void loadCrossOrigin(Object bean, Consumer<Catcher> consumer) {
        // 当目标对象本身又被别的代理包装时, 能拿到真实类而不是代理类。
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);

        // 遍历 Controller 的方法，包括继承的方法
        ReflectionUtils.doWithMethods(
                targetClass,
                method -> {
                    // 查找 @CrossOrigin
                    CrossOrigin crossOrigin = resolveAnnotation(targetClass, method, CrossOrigin.class);
                    if(crossOrigin == null)
                        return;

                    Mapping mapping = resolveAnnotation(targetClass, method, Mapping.class);
                    if(noneMappingMethod(method, mapping))
                        return;

                    HandlerInterceptor interceptor = new CorsInterceptor(this.initCorsConfiguration(crossOrigin, mapping.method()));
                    consumer.accept(new CorsCatcher(mapping.value(), interceptor));
                    log.info("Loaded cross origin: {}, {}", mapping.value(), interceptor);
                },
                SpringContainerAware::isValidMethod
        );
    }

    @Override
    public void destroy() {
        // Let Spring destroy all beans.
        // Only call close() on WebApplicationContext
        if (ContextSupport.getApplicationContext() instanceof ConfigurableApplicationContext) {
            ConfigurableApplicationContext context = (ConfigurableApplicationContext) ContextSupport.getApplicationContext();
            if (context.isActive())
                context.close();
        }
    }

    private void loadMapping(Object bean, BiConsumer<String, Execution> biConsumer) {
        // 当目标对象本身又被别的代理包装时, 能拿到真实类而不是代理类。
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);

        // 遍历 Controller 的方法，包括继承的方法
        ReflectionUtils.doWithMethods(
                targetClass,
                method -> {
                    // 查找 @Mapping
                    Mapping mapping = resolveAnnotation(targetClass, method, Mapping.class);
                    if(noneMappingMethod(method, mapping))
                        return;

                    Execution execution = new Execution(bean, method, mapping);
                    Arrays.stream(mapping.value()).forEach(route -> biConsumer.accept(route, execution));
                },
                SpringContainerAware::isValidMethod
        );
    }

    private void loadInterceptor(Object bean, Consumer<Catcher> consumer) {
        // 当目标对象本身又被别的代理包装时, 能拿到真实类而不是代理类。
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);

        // if Interceptor
        if (bean instanceof HandlerInterceptor) {
            Interceptor interceptor = AnnotatedElementUtils.findMergedAnnotation(targetClass, Interceptor.class);

            log.info("Loaded Interceptor: [order={}, interceptors={}, excludes={}]",
                    interceptor.order(), interceptor.value(), interceptor.excludes());
            consumer.accept(new CatcherMapping(interceptor, (HandlerInterceptor) bean));
            return;
        }

        throw new NotSupportedException("The interceptor needs to implement the HandlerInterceptor interface or inherit the AbstractHandlerInterceptor class. '" + bean.getClass().getName() + "'");
    }

    private CorsConfiguration initCorsConfiguration(CrossOrigin cross, RequestMethod[] requestMethods) {
        CorsConfiguration config = new CorsConfiguration();

        // allowed domains
        if (ArrayUtils.isEmpty(cross.origins()))
            config.addAllowedOrigin(CorsConfiguration.ALL);
        else
            Arrays.stream(cross.origins()).forEach(config::addAllowedOrigin);

        // Cookie/Credentials
        config.setAllowCredentials(
                StringUtils.isEmpty(cross.allowCredentials()) ?
                        Boolean.TRUE : BooleanUtils.toBoolean(cross.allowCredentials()));

        // allowed methods
        if (ArrayUtils.isEmpty(cross.methods())) {
            if (ArrayUtils.isEmpty(requestMethods))
                config.addAllowedMethod(CorsConfiguration.ALL);
            else
                Arrays.stream(requestMethods).forEach(method -> config.addAllowedMethod(method.name()));
        } else
            Arrays.stream(cross.methods()).forEach(method -> config.addAllowedMethod(method.name()));

        // allowed headers
        if (ArrayUtils.isEmpty(cross.allowedHeaders()))
            config.addAllowedHeader(CorsConfiguration.ALL);
        else
            Arrays.stream(cross.allowedHeaders()).forEach(config::addAllowedHeader);

        // expose headers
        if (ArrayUtils.isNotEmpty(cross.exposedHeaders()))
            Arrays.stream(cross.exposedHeaders()).forEach(config::addExposedHeader);

        if (cross.maxAge() > -1L)
            config.setMaxAge(cross.maxAge());

        return config;
    }

    public static boolean isValidMethod(Method method) {
        if (method == null || method.isBridge() || method.isSynthetic())
            return false;

        if (Modifier.isStatic(method.getModifiers())) {
            log.warn("Invalid Action method '{}' is static.", method.toGenericString());
            return false;
        }
        return true;
    }

    public static boolean noneMappingMethod(Method method, Mapping mapping) {
        if(mapping == null)
            return true;

        if (ArrayUtils.isEmpty(mapping.value())) {
            log.warn("Invalid Action method '{}', URI mapping value cannot be empty.", method.toGenericString());
            return true;
        }

        if(ArrayUtils.isEmpty(mapping.method())){
            log.warn("Invalid Action method '{}', URI mapping method cannot be empty.", method.toGenericString());
            return true;
        }

        Class<?>[] argTypes = method.getParameterTypes();
        ConverterFactory converter = ConverterFactory.getInstance();

        for (Class<?> argType : argTypes) {
            if (!converter.isConvert(argType)) {
                log.warn("Invalid Action method '{}' unsupported parameter type '{}'.", method.toGenericString(), argType.getName());
                return true;
            }
        }

        return false;
    }
}
