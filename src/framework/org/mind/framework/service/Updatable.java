package org.mind.framework.service;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.web.dispatcher.support.ConverterFactory;

/**
 * @author marcus
 */
public interface Updatable {

    void doUpdate();

    /**
     * Read the startup command parameter: -Dsvc.replica=yes,
     */
    static boolean getEnvReplica() {
        String replica = getEnvArgs(Service.SVC_REPLICA_NAME_ENV);
        if(StringUtils.isNotBlank(replica))
            return BooleanUtils.toBoolean(replica);

        return BooleanUtils.toBoolean(getRunArgs(Service.SVC_REPLICA_NAME, BooleanUtils.FALSE));
    }

    /**
     * Read the startup command parameter: -app.instance=work-node-1,
     */
    static String getAppInstanceId() {
        String instanceId = getEnvArgs(Service.APP_INSTANCE_ID_ENV);
        if(StringUtils.isNotBlank(instanceId))
            return instanceId;

        return getRunArgs(Service.APP_INSTANCE_ID);
    }

    static String getEnvArgs(String key){
        return System.getenv(key);
    }

    static String getEnvArgs(String key, String defaultValue){
        return StringUtils.defaultIfBlank(getEnvArgs(key), defaultValue);
    }

    static String getRunArgs(String key){
        return System.getProperty(key);
    }

    static String getRunArgs(String key, String defaultValue){
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

    static <T> T getRunArgs(String key, Class<T> parseClazz){
        String value = getRunArgs(key);
        if(StringUtils.isEmpty(value))
            return null;

        ConverterFactory converterFactory = ConverterFactory.getInstance();
        if(converterFactory.isConvert(parseClazz))
            return converterFactory.convert(parseClazz, value);

        return (T) value;
    }
}
