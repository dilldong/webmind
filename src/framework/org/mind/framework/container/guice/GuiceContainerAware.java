package org.mind.framework.container.guice;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Stage;
import org.mind.framework.container.ContainerAware;
import org.mind.framework.container.Destroyable;
import org.mind.framework.container.ServletContextAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Create Guice 4.0 Injector instance, and bind it on ServletContext with name
 * of <code>Injector.class.getName()</code>.
 *
 * @author dp
 */
public class GuiceContainerAware implements ContainerAware {

    private static final Logger log = LoggerFactory.getLogger(GuiceContainerAware.class);

    private Injector injector;

    public List<Object> loadBeans() {
        Map<Key<?>, Binding<?>> map = injector.getBindings();
        Set<Map.Entry<Key<?>, Binding<?>>> set = map.entrySet();
        List<Object> list = new ArrayList<Object>(set.size());

        for (Map.Entry<Key<?>, Binding<?>> entry : set) {
//			BeanModel model = new BeanModel(
//					injector.getInstance(entry.getKey()), 
//					true);

            list.add(injector.getInstance(entry.getKey()));
        }

        return list;
    }

    public void init(final ServletConfig config) {
        String value = config.getInitParameter("modules");
        if (value == null)
            throw new IllegalArgumentException("init Guice failed. missing parameter '<modules>' by web.xml.");

        String[] strs = value.split("\\,");
        List<Module> moduleList = new ArrayList<Module>(strs.length);

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

    private Module initModule(String name, ServletContext servletContext) {
        if (name.length() > 0) {
            log.info("Initializing module '{}'....", name);

            try {
                Object obj = Class.forName(name).newInstance();
                if (obj instanceof Module) {
                    if (obj instanceof ServletContextAware) {
                        ((ServletContextAware) obj).setServletContext(servletContext);
                    }
                    return (Module) obj;
                }

                throw new IllegalArgumentException(
                        String.format("Class '%s' does not implement '%s'.", name, Module.class.getName()));

            } catch (InstantiationException e) {
                throw new IllegalArgumentException(String.format("Cannot instanciate class '%s'.", name), e);
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException(String.format("Cannot instanciate class '%s'.", name), e);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(String.format("Cannot instanciate class '%s'.", name), e);
            }
        }
        return null;
    }

    public void destroy() {
        List<Object> beans = this.loadBeans();
        for (Object bean : beans) {
            if (bean instanceof Destroyable)
                ((Destroyable) bean).destroy();
        }
    }

}
