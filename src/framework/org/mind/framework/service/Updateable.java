package org.mind.framework.service;

import org.apache.commons.lang3.BooleanUtils;
import org.mind.framework.container.Destroyable;

public interface Updateable extends Destroyable {

    void doUpdate();

    /**
     * Read the startup command parameter: -Dsvc.replica=yes,
     * <br/>Can be used to on/off work under multiple instances.
     * @return
     */
    static boolean getEnvReplica() {
        return BooleanUtils.toBoolean(
                System.getProperty(Service.SVC_REPLICA_NAME, "false"));
    }
}
