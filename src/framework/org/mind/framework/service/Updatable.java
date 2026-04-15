package org.mind.framework.service;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.web.dispatcher.support.ConverterFactory;

public interface Updatable {

    void doUpdate();

    /**
     * Read the startup command parameter: -Dsvc.replica=yes,
     */
    static boolean getEnvReplica() {
        return BooleanUtils.toBoolean(
                getEnvArgs(Service.SVC_REPLICA_NAME, "false"));
    }

    /**
     * Read the startup command parameter: -app.instance=work-node-1,
     */
    static String getAppInstanceId() {
        return getEnvArgs(Service.APP_INSTANCE_ID);
    }

    static String getEnvArgs(String key){
        return System.getProperty(key);
    }

    static String getEnvArgs(String key, String defaultValue){
        return System.getProperty(key, defaultValue);
    }

    static <T> T getEnvArgs(String key, Class<T> parseClazz){
        String value = getEnvArgs(key);
        if(StringUtils.isEmpty(value))
            return null;

        ConverterFactory converterFactory = ConverterFactory.getInstance();
        if(converterFactory.isConvert(parseClazz))
            return converterFactory.convert(parseClazz, value);

        return (T) value;
    }
}
