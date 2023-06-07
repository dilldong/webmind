package org.mind.framework.container;

import javax.servlet.ServletConfig;
import java.util.List;

/**
 * Web container instance for creating IOC container.
 *
 * @author dp
 */
public interface ContainerAware extends Destroyable {

    void init(ServletConfig config);

    List<Object> loadBeans();
}
