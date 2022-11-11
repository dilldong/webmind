package org.mind.framework.container.spring;

import org.mind.framework.ContextSupport;
import org.mind.framework.container.ContainerAware;

import javax.servlet.ServletConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Web Container Wrapper for Spring 2+
 *
 * @author dp
 */
public class SpringContainerAware implements ContainerAware {

    public List<Object> loadBeans() {
        //get defined name by spring
        String[] names = ContextSupport.getBeanNames();
        List<Object> beans = new ArrayList<>(names.length);
        Arrays.stream(names).parallel().forEach(name -> beans.add(ContextSupport.getBean(name)));
        return beans;
    }

    public void init(ServletConfig config) {
        ContextSupport.initWebContext(config.getServletContext());
    }

    public synchronized void destroy() {
        // nothing to do, let spring destroy all beans.
    }

}
