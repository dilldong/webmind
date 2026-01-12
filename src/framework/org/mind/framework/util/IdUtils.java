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

    private static final AtomicInteger ATOMIC_INTEGER = new AtomicInteger(0);

    /**
     * 按当前日期(6位长度) + ObjectId的自增计数器(取后6位长度)
     *
     * @return
     */
    public static long getUniqueId() {
        return getUniqueId(DateUtils.SIMPLE_DATE_PATTERN);
    }

    public static long getUniqueIdPrefixUTC() {
        return getUniqueId(DateUtils.SIMPLE_DATE_PATTERN, true);
    }

    /**
     * 按日期格式 + ObjectId的自增计数器(取后6位长度)
     *
     * @return
     */
    public static long getUniqueId(String pattern) {
        return getUniqueId(pattern, false);
    }

    public static long getUniqueIdPrefixUTC(String pattern) {
        return getUniqueId(pattern, true);
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

            long machine = Long.parseLong(objId.substring(8, 14), 16);
            long pid = Long.parseLong(objId.substring(14, 18), 16);
            long inc = Long.parseLong(objId.substring(18), 16);
            return String.join(StringUtils.EMPTY, String.valueOf(machine), String.valueOf(pid), String.valueOf(inc));
        } catch (Exception e) {
            logger.error(e.getMessage());
            return String.valueOf(System.currentTimeMillis() + ATOMIC_INTEGER.incrementAndGet());
        }
    }

    public static int generateId() {
        int atomic = ATOMIC_INTEGER.incrementAndGet();
        long timeMillis = DateUtils.currentMillis();
        String value = StringUtils.substring(String.valueOf(timeMillis + atomic), 5);
        return Integer.parseInt(value);
    }

    private static long getUniqueId(String pattern, boolean isUTC) {
        try {
            ObjectId objId = ObjectId.get();
            String date = isUTC?
                    DateUtils.formatUTC(objId.getDate(), pattern) :
                    DateUtils.format(objId.getDate(), pattern);
            String counter = String.valueOf(objId.getCounter());
            int length = counter.length();
            if (length > 6)
                counter = StringUtils.substring(counter, length - 6);

            return Long.parseLong(date + counter);
        } catch (Exception e) {
            logger.error(e.getMessage());
            return System.currentTimeMillis() + ATOMIC_INTEGER.incrementAndGet();
        }
    }
}
