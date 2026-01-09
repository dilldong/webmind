package org.mind.framework.web.container.guice;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Stage;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.web.Destroyable;
import org.mind.framework.web.container.ContainerAware;
import org.mind.framework.web.container.ServletContextAware;
import org.mind.framework.web.dispatcher.handler.Execution;
import org.mind.framework.web.dispatcher.support.Catcher;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Create Guice 4.0 Injector instance, and bind it on ServletContext with name
 * of <code>Injector.class.getName()</code>.
 *
 * @author dp
 */
@Slf4j(topic = "GuiceContainer")
public class GuiceContainerAware implements ContainerAware {

    private Injector injector;

    @Getter
    private ServletConfig servletConfig;

    @Override
    public void init(ServletConfig config) {
        this.servletConfig = config;
        String value = config.getInitParameter("modules");
        if (value == null)
            throw new IllegalArgumentException("init Guice failed. missing parameter '<modules>' by web.xml.");

        String[] strs = value.split("\\,");
        List<Module> moduleList = new ArrayList<>(strs.length);

        for (String name : strs) {
            Module m = initModule(name.trim(), config.getServletContext());
            if (m != null)
                moduleList.add(m);
        }

        if (moduleList.isEmpty())
            throw new IllegalStateException("Not found Guice Modules.");

        this.injector = Guice.createInjector(Stage.PRODUCTION, moduleList);
        config.getServletContext()
                .setAttribute(
                        Injector.class.getName(),
                        this.injector);
    }

    @Override
    public ServletContext getServletContext() {
        return this.getServletConfig().getServletContext();
    }

    @Override
    public List<Object> loadBeans(boolean ... exclude) {
        Map<Key<?>, Binding<?>> map = injector.getBindings();
        Set<Map.Entry<Key<?>, Binding<?>>> set = map.entrySet();
        List<Object> list = new ArrayList<>(set.size());

        for (Map.Entry<Key<?>, Binding<?>> entry : set) {
//			BeanModel model = new BeanModel(
//					injector.getInstance(entry.getKey()),
//					true);

            list.add(injector.getInstance(entry.getKey()));
        }

        return list;
    }

    @Override
    public void loadInterceptor(Object bean, Consumer<Catcher> consumer) {

    }

    @Override
    public void loadMapping(Object bean, BiConsumer<String, Execution> biConsumer) {

    }

    @Override
    public boolean isMappingMethod(Method method) {
        return false;
    }

    @Override
    public void loadCorsOrigin(Object bean, Consumer<Catcher> consumer) {

    }

    @Override
    public synchronized void destroy() {
        List<Object> beans = this.loadBeans();
        for (Object bean : beans) {
            if (bean instanceof Destroyable)
                ((Destroyable) bean).destroy();
        }
    }

    private Module initModule(String name, ServletContext servletContext) {
        if (name.length() > 0) {
            log.info("Initializing module '{}'....", name);

            try {
                Object obj = ClassUtils.newInstance(name);
                if (obj instanceof Module) {
                    if (obj instanceof ServletContextAware) {
                        ((ServletContextAware) obj).setServletContext(servletContext);
                    }
                    return (Module) obj;
                }

                throw new IllegalArgumentException(
                        String.format("Class '%s' does not implement '%s'.", name, Module.class.getName()));

            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                log.error("Cannot instantiate class '{}'.", name);
                ThrowProvider.doThrow(e);
            }
        }
        return null;
    }
}
