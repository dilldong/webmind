package org.mind.framework.service;

import org.apache.commons.lang3.BooleanUtils;

public interface Updatable {

    void doUpdate();

    /**
     * Read the startup command parameter: -Dsvc.replica=yes,
     * <br/>Can be used to on/off work under multiple instances.
     *
     * @return
     */
    static boolean getEnvReplica() {
        return BooleanUtils.toBoolean(
                System.getProperty(Service.SVC_REPLICA_NAME, "false"));
    }
}
