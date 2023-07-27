package org.mind.framework.service;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.dispatcher.support.ConverterFactory;

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

    static String getEnvArgs(String key){
        return System.getProperty(key);
    }

    static <T> T getEnvArgs(String key, Class<T> parseClazz){
        String value = getEnvArgs(key);
        if(StringUtils.isEmpty(value))
            return null;

        ConverterFactory converterFactory = ConverterFactory.getInstance();
        if(converterFactory.isConvert(parseClazz))
            return (T) converterFactory.convert(parseClazz, value);

        return (T) value;
    }
}
