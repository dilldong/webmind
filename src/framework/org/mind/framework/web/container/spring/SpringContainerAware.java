package org.mind.framework.web.container.spring;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.ContextSupport;
import org.mind.framework.annotation.Interceptor;
import org.mind.framework.annotation.Mapping;
import org.mind.framework.exception.NotSupportedException;
import org.mind.framework.web.container.ContainerAware;
import org.mind.framework.web.dispatcher.handler.Execution;
import org.mind.framework.web.dispatcher.support.Catcher;
import org.mind.framework.web.dispatcher.support.CatcherMapping;
import org.mind.framework.web.dispatcher.support.ConverterFactory;
import org.mind.framework.web.interceptor.AbstractHandlerInterceptor;
import org.mind.framework.web.interceptor.CorsCatcher;
import org.mind.framework.web.interceptor.CorsInterceptor;
import org.mind.framework.web.interceptor.HandlerInterceptor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.cors.CorsConfiguration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

    @Override
    public List<Object> loadBeans(boolean... excludeSpringSelf) {
        String[] names = ContextSupport.getBeanNames();
        //get defined name by spring
        List<String> beanNames = (ArrayUtils.isEmpty(excludeSpringSelf) || !excludeSpringSelf[0]) ?
                Arrays.asList(names) :
                Arrays.stream(names)
                        .filter(name -> !StringUtils.startsWith(name, "org.springframework"))
                        .collect(Collectors.toList());

        List<Object> beans = new ArrayList<>(beanNames.size());
        beanNames.forEach(name -> beans.add(ContextSupport.getBean(name)));
        return Collections.unmodifiableList(beans);
    }

    @Override
    public void loadInterceptor(Object bean, Consumer<Catcher> consumer) {
        Class<?> clazz = bean.getClass();

        // if Interceptor
        if (clazz.isAnnotationPresent(Interceptor.class)) {
            if (AbstractHandlerInterceptor.class.isAssignableFrom(clazz) || HandlerInterceptor.class.isAssignableFrom(clazz)) {
                Interceptor interceptor = clazz.getAnnotation(Interceptor.class);

                log.info("Loaded Interceptor: [order={}, interceptors={}, excludes={}]",
                        interceptor.order(), interceptor.value(), interceptor.excludes());
                consumer.accept(new CatcherMapping(interceptor, (HandlerInterceptor) bean));
                return;
            }

            throw new NotSupportedException("The interceptor needs to implement the HandlerInterceptor interface or inherit the AbstractHandlerInterceptor class. '" + bean.getClass().getName() + "'");
        }
    }

    /**
     * Initialize all Bean objects, and {@link Mapping} increased URI mapping.
     */
    @Override
    public void loadMapping(Object bean, BiConsumer<String, Execution> biConsumer) {
        StringJoiner joiner = new StringJoiner(", ");

        // Mapping
        Class<?> clazz = bean.getClass();
        Method[] methods = clazz.getMethods();

        for (Method method : methods) {
            if (!this.isMappingMethod(method))
                continue;

            Mapping mapping = method.getAnnotation(Mapping.class);
            biConsumer.accept(
                    mapping.value(),
                    new Execution(bean, method, mapping.method(), mapping.requestLog()));

            joiner.add(mapping.value());
        }

        if (joiner.length() > 0)
            log.info("Loaded URI mapping: {}", joiner);
    }

    @Override
    public void loadCorsOrigin(Object bean, Consumer<Catcher> consumer) {
        // CorsOrigin
        Class<?> clazz = bean.getClass();
        CrossOrigin classOrigin = AnnotatedElementUtils.findMergedAnnotation(clazz, CrossOrigin.class);
        Method[] methods = clazz.getMethods();

        for (Method method : methods) {
            if (!this.isMappingMethod(method))
                continue;

            CrossOrigin methodOrigin = AnnotatedElementUtils.findMergedAnnotation(method, CrossOrigin.class);
            if (Objects.isNull(methodOrigin) && Objects.isNull(classOrigin))
                continue;

            Mapping mapping = method.getAnnotation(Mapping.class);
            HandlerInterceptor interceptor =
                    Objects.isNull(methodOrigin) ?
                            new CorsInterceptor(this.initCorsConfiguration(classOrigin, mapping.method())) :
                            new CorsInterceptor(this.initCorsConfiguration(methodOrigin, mapping.method()));

            consumer.accept(new CorsCatcher(new String[]{mapping.value()}, interceptor));
            log.info("Loaded cross origin: [{}], {}", mapping.value(), interceptor);
        }
    }

    @Override
    public boolean isMappingMethod(Method method) {
        Mapping mapping = method.getAnnotation(Mapping.class);
        if (Objects.isNull(mapping))
            return false;

        if (StringUtils.trimToEmpty(mapping.value()).length() == 0) {
            log.warn("Invalid Action method '{}', URI mapping value cannot be empty.", method.toGenericString());
            return false;
        }

        if (Modifier.isStatic(method.getModifiers())) {
            log.warn("Invalid Action method '{}' is static.", method.toGenericString());
            return false;
        }

        Class<?>[] argTypes = method.getParameterTypes();
        ConverterFactory converter = ConverterFactory.getInstance();

        for (Class<?> argType : argTypes) {
            if (!converter.isConvert(argType)) {
                log.warn("Invalid Action method '{}' unsupported parameter type '{}'.", method.toGenericString(), argType.getName());
                return false;
            }
        }

        return true;
    }

    @Override
    public void destroy() {
        // Let Spring destroy all beans.
        // Only call close() on WebApplicationContext
        if (ContextSupport.getApplicationContext() instanceof ConfigurableApplicationContext)
            ((ConfigurableApplicationContext) ContextSupport.getApplicationContext()).close();
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

}
