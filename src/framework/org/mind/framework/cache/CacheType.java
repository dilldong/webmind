package org.mind.framework.cache;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.Strings;

/**
 * @author Marcus
 * @version 1.0
 * @date 2026/8/23
 */
@AllArgsConstructor
@Getter
public enum CacheType {
    OBJECT("string"),
    MAP("hash"),
    LIST("list"),
    SET("set"),
    ZSET("zset"),
    STREAM("stream"),
    JSON("ReJSON-RL");

    private final String value;

    public static CacheType find(String value){
        CacheType[] types = CacheType.values();
        for (CacheType type : types) {
            if(Strings.CS.equalsAny(value, type.name(), type.value))
                return type;
        }

        return null;
    }
}
