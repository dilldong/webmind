package org.mind.framework.container;

/**
 * Object which has resource to release should implement this interface.
 * 
 * @author dp
 */
public interface Destroyable {

    /**
     * Destroy the object.
     */
    void destroy();

}
