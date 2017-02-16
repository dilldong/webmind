package org.mind.framework.container;

import java.util.List;

import javax.servlet.ServletConfig;

/**
 * Web container instance for creating IOC container.
 * @author dp
 *
 */
public interface ContainerAware extends Destroyable {

    void init(ServletConfig config);

    List<Object> loadBeans();
}
