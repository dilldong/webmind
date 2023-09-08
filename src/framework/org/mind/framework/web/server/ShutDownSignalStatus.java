package org.mind.framework.web.server;

import lombok.AllArgsConstructor;
import lombok.ToString;

@ToString
@AllArgsConstructor
public enum ShutDownSignalStatus {
    UNSTARTED(0),
    IN(1),
    PAUSE(2),
    DOWN(3),
    OUT(-1);

    public final int code;

    public static ShutDownSignalStatus find(int code){
        ShutDownSignalStatus[] signalArray = ShutDownSignalStatus.values();
        for(ShutDownSignalStatus signal : signalArray){
            if(signal.code == code)
                return signal;
        }
        return null;
    }
}
