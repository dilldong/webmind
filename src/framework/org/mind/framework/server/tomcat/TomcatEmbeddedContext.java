package org.mind.framework.server.tomcat;

import org.apache.catalina.Manager;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.ManagerBase;

/**
 * @author Marcus
 * @version 1.0
 */
public class TomcatEmbeddedContext extends StandardContext {
    @Override
    public void setManager(Manager manager) {
        if (manager instanceof ManagerBase)
            manager.setSessionIdGenerator(new LazySessionIdGenerator());
        
        super.setManager(manager);
    }
}
