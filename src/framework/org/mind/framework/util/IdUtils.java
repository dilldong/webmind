package org.mind.framework.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 唯一id
 */
public class IdUtils {

    private static final Logger logger = LoggerFactory.getLogger(IdUtils.class);

    private static final AtomicInteger atomicInteger = new AtomicInteger();

    /**
     * 获取唯一id
     *
     * @return
     */
    public static Long getUniqueId() {
        try {
            // 16进制，4位时间码，3位机器码，2位进程id，3位自增计数器
            ObjectId objId = new ObjectId();

            /* Long machine = Long.parseLong(objId.substring(8, 8 + 6), 16); */
//            Long PID = Long.parseLong(objId.substring(14, 14 + 4), 16);
//            Long INC = Long.parseLong(objId.substring(18), 16);
            String date = DateFormatUtils.format(objId.getDate(), "yyMMdd");
            String counter = String.valueOf(objId.getCounter());
            int length = counter.length();
            if (length > 6)
                counter = StringUtils.substring(counter, length - 6);

            return Long.parseLong(String.format("%s%s", date, counter));
        } catch (NumberFormatException e) {
            logger.error(e.getMessage());
            return System.currentTimeMillis();
        }
    }

    public static int generateId() {
        int atomic = atomicInteger.incrementAndGet();
        long timeMillis = DateFormatUtils.getTimeMillis();
        String value = StringUtils.substring(String.valueOf(timeMillis + atomic), 5);
        return Integer.parseInt(value);
    }


    public static void main(String[] args) {
        System.out.println(getUniqueId());
        System.out.println(generateId());

        ObjectId objId = new ObjectId();
        System.out.println("d: " + DateFormatUtils.format(objId.getDate(), "yyyy-MM-dd HH:mm:ss") + "\tcounter: " + objId.getCounter() + "\tMid : " + objId.getMachineIdentifier() + "\tPID: " + objId.getProcessIdentifier());
    }
}
