package org.mind.framework.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 唯一id
 */
public class IdUtils {

    private static final Logger logger = LoggerFactory.getLogger(IdUtils.class);

    private static final AtomicInteger atomicInteger = new AtomicInteger(0);

    /**
     * 按当前日期(6位长度) + ObjectId的自增计数器(取后6位长度)
     *
     * @return
     */
    public static long getUniqueId() {
        try {
            ObjectId objId = ObjectId.get();
            String date = DateFormatUtils.format(objId.getDate(), "yyMMdd");
            String counter = String.valueOf(objId.getCounter());
            int length = counter.length();
            if (length > 6)
                counter = StringUtils.substring(counter, length - 6);

            return Long.parseLong(String.format("%s%s", date, counter));
        } catch (NumberFormatException e) {
            logger.error(e.getMessage());
            return System.currentTimeMillis() + atomicInteger.incrementAndGet();
        }
    }

    /**
     * 20位长度的随机数ID
     *
     * @return
     */
    public static BigInteger getObjectId4BigInt(){
        String objId = getObjectId();
        return new BigInteger(objId);
    }

    /**
     * 20位长度的随机数ID
     *
     * @return
     */
    public static String getObjectId() {
        try {
            // (16进制) 由4位时间码, 3位机器码, 2位进程id, 3位自增计数器组成
            String objId = ObjectId.get().toHexString();

            Long machine = Long.parseLong(objId.substring(8, 14), 16);
            Long pid = Long.parseLong(objId.substring(14, 18), 16);
            Long inc = Long.parseLong(objId.substring(18), 16);
            return String.format("%d%d%d", machine, pid, inc);
        } catch (NumberFormatException e) {
            logger.error(e.getMessage());
            return String.format("%d", System.currentTimeMillis() + atomicInteger.incrementAndGet());
        }
    }

    public static int generateId() {
        int atomic = atomicInteger.incrementAndGet();
        long timeMillis = DateFormatUtils.getMillis();
        String value = StringUtils.substring(String.valueOf(timeMillis + atomic), 5);
        return Integer.parseInt(value);
    }
}
