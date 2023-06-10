package org.mind.framework.server;

import lombok.AllArgsConstructor;
import lombok.ToString;

@ToString
@AllArgsConstructor
public enum ShutDownSignalEnum {
    UNSTARTED(0),
    IN(1),
    PAUSE(2),
    DOWN(3),
    OUT(-1);

    public final int code;

    public static ShutDownSignalEnum find(int code){
        ShutDownSignalEnum[] signalArray = ShutDownSignalEnum.values();
        for(ShutDownSignalEnum signal : signalArray){
            if(signal.code == code)
                return signal;
        }
        return null;
    }
}
