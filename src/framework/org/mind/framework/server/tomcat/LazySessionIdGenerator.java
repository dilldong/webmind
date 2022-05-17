package org.mind.framework.server.tomcat;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.util.StandardSessionIdGenerator;

/**
 * @author Marcus
 * @version 1.0
 */
class LazySessionIdGenerator extends StandardSessionIdGenerator {

    @Override
    protected void startInternal() throws LifecycleException {
        super.setState(LifecycleState.STARTING);
    }
}
