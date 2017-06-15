package org.mind.framework.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.log4j.Logger;

/**
 * 对日期、时间格式化工具类
 *
 * @author dongping
 */
public class DateFormatUtils {

    static Logger logger = Logger.getLogger(DateFormatUtils.class);

    /**
     * 返回时间的毫秒数，同System.currentTimeMillis()结果一致.
     *
     * @return
     * @author dongping
     */
    public static long getTimeMillis() {
        return Calendar.getInstance().getTimeInMillis();
    }

    /**
     * 将指定特殊的格式，返回格式化的时间；
     * 如果不是特别的日期格式，请使用该类的其它方法.
     *
     * @param date
     * @param format
     * @return
     * @author dongping
     */
    public static String format(Date date, String format) {
        if (date == null)
            date = Calendar.getInstance().getTime();

        if (format == null || format.isEmpty())
            return getDateTime(date);

        return new SimpleDateFormat(format).format(date);
    }

    /**
     * 需要指定日期，如果该值为null，将返回当前日期下的时间值，
     * 格式类型是：yyyy-MM-dd HH:mm:ss
     *
     * @param date
     * @return
     * @author dongping
     */
    public static String format(Date date) {
        return format(date, null);
    }

    public static String getDate() {
        return getDate(Calendar.getInstance().getTime());
    }

    public static String getDate(Date date) {
        return java.text.DateFormat.getDateInstance().format(date);
    }

    public static String getDateTime() {
        return getDateTime(Calendar.getInstance().getTime());
    }

    public static String getDateTime(Date date) {
        return java.text.DateFormat.getDateTimeInstance().format(date);
    }

    public static String getTime() {
        return getTime(Calendar.getInstance().getTime());
    }

    public static String getTime(Date date) {
        return java.text.DateFormat.getTimeInstance().format(date);
    }

    public static String getFullDate() {
        return getFullDate(Calendar.getInstance().getTime());
    }

    public static String getFullDate(Date date) {
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(date);
    }

    //----------------------------------------------------------------------------

    public static Date getCurrentDate() {
        return Calendar.getInstance().getTime();
    }

    /**
     * 将指定的字符串日期转化成java.util.Date类型<br>
     * <b>注意字符日期格式必须是：yyyy-MM-dd</b>
     */
    public static Date toDate(String source) {
        try {
            return java.text.DateFormat.getDateInstance().parse(source);
        } catch (ParseException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 将指定的字符串日期转化成java.util.Date类型<br>
     * <b>注意字符日期格式必须是：yyyy-MM-dd HH:mm:ss</b>
     */
    public static Date toDateTime(String source) {
        try {
            return java.text.DateFormat.getDateTimeInstance().parse(source);
        } catch (ParseException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 将指定的字符串日期转化成java.util.Date类型，
     * 这里的字符日期格式必须可以是pattern参数指定的任何格式
     */
    public static Date toSimple(String source, String pattern) {
        java.text.DateFormat df = new SimpleDateFormat(pattern);
        try {
            return df.parse(source);
        } catch (ParseException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

}
