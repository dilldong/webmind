package org.mind.framework.container.spring;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletConfig;

import org.mind.framework.ContextSupport;
import org.mind.framework.container.ContainerAware;

/**
 * Web Container Wrapper for Spring 2.x.
 *
 * @author dp
 */
public class SpringContainerAware implements ContainerAware {


    public List<Object> loadBeans() {
        //get defined name by spring
        String[] names = ContextSupport.getBeanNames();
        List<Object> beans = new ArrayList<Object>(names.length);

        for (String name : names) {
//        	BeanModel model = new BeanModel(
//        			this.wctx.getBean(name), 
//        			this.wctx.isSingleton(name));

            beans.add(ContextSupport.getBean(name));
        }

        return beans;
    }

    public void init(ServletConfig config) {
        ContextSupport.initSpringContext(config.getServletContext());
    }

    public void destroy() {
        // nothing to do, let spring destroy all beans.
    }

}
