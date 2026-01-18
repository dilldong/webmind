package org.mind.framework.web.container;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import org.mind.framework.web.Destroyable;
import org.mind.framework.web.dispatcher.handler.Execution;
import org.mind.framework.web.dispatcher.support.Catcher;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Web container instance for creating IOC container.
 *
 * @author dp
 */
public interface ContainerAware extends Destroyable {

    void init(ServletConfig config);

    ServletContext getServletContext();

    ServletConfig getServletConfig();

    List<Object> loadBeans(boolean ... excludeSpringSelf);

    void loadInterceptor(Object bean, Consumer<Catcher> consumer);

    void loadMapping(Object bean, BiConsumer<String, Execution> biConsumer);

    void loadCorsOrigin(Object bean, Consumer<Catcher> consumer);

    boolean isMappingMethod(Method method);
}
