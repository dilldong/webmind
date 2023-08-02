package org.mind.framework.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;

/**
 * 对日期、时间格式化工具类
 *
 * @author dp
 */
public class DateFormatUtils {

    static final Logger log = LoggerFactory.getLogger(DateFormatUtils.class);

    public static final ZoneId ZONE_DEFAULT = ZoneId.systemDefault();
    public static final ZoneId UTC = ZoneId.of("UTC");
    public static final ZoneId UTC8 = ZoneId.of("UTC+8");
    public static final TimeZone UTC_TIMEZONE = TimeZone.getTimeZone(UTC);
    public static final TimeZone UTC8_TIMEZONE = TimeZone.getTimeZone(UTC8);
    public static final long ONE_DAY_MILLIS = 86_400_000L;
    public static final long ONE_HOUR_MILLIS = 3_600_000L;


    /**
     * 返回时间的毫秒数，同System.currentTimeMillis()结果一致.
     *
     * @return
     * @author dp
     */
    public static long getMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 返回时间的秒数
     *
     * @return
     * @author dp
     */
    public static long getSeconds() {
        return getMillis() / 1000L;
    }



    /**
     * 返回某天之前的时间戳
     *
     * @param days
     * @return
     */
    public static long before(int days) {
        if (days < 1)
            return -1L;
        return getMillis() - days * ONE_DAY_MILLIS;
    }

    /**
     * 返回某天之前的起始时间戳
     *
     * @param days
     * @return
     */
    public static long beforeAtStartOfDay(int days) {
        if (days < 1)
            return -1L;
        return beforeAtStartOfDay(days, ZONE_DEFAULT);
    }

    /**
     * 指定时区, 返回某天之前的起始时间戳
     *
     * @param days
     * @param zoneId
     * @return
     */
    public static long beforeAtStartOfDay(int days, ZoneId zoneId) {
        long timestamp = before(days);
        return timestamp == -1 ? timestamp : startOfDayMillis(dateAt(timestamp, zoneId), zoneId);
    }


    /**
     * 将指定特殊的格式，返回格式化的时间；
     * 如果不是特别的日期格式，请使用该类的其它方法.
     *
     * @param date
     * @param format
     * @return
     * @author dp
     */
    public static String format(Date date, String format) {
        if (Objects.isNull(date))
            date = currentDate();

        if (StringUtils.isEmpty(format))
            return getDateTime(date);

        return new SimpleDateFormat(format).format(date);
    }

    /**
     * 需要指定日期，如果该值为null，将返回当前日期下的时间值，
     * 格式类型是：yyyy-MM-dd HH:mm:ss
     *
     * @param date
     * @return
     * @author dp
     */
    public static String format(Date date) {
        return format(date, null);
    }

    public static String getDate() {
        return getDate(currentDate());
    }

    public static String getDate(Date date) {
        return java.text.DateFormat.getDateInstance().format(date);
    }

    public static String getDateTime() {
        return getDateTime(currentDate());
    }

    public static String getDateTime(Date date) {
        return java.text.DateFormat.getDateTimeInstance().format(date);
    }

    public static String getTime() {
        return getTime(currentDate());
    }

    public static String getTime(Date date) {
        return java.text.DateFormat.getTimeInstance().format(date);
    }

    public static String getFullDate() {
        return getFullDate(currentDate());
    }

    public static String getFullDate(Date date) {
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(date);
    }

    public static Date currentDate() {
        return new Date(getMillis());
    }

    public static Timestamp currentTimestamp() {
        return currentTimestamp(getMillis());
    }

    public static Timestamp currentTimestamp(Date date) {
        return currentTimestamp(date.getTime());
    }

    public static Timestamp currentTimestamp(long timeMillis) {
        return new Timestamp(timeMillis);
    }

    public static LocalDate dateNow() {
        return dateNow(ZONE_DEFAULT);
    }

    public static LocalDate utcDateNow() {
        return dateNow(UTC);
    }

    public static LocalDate dateNow(ZoneId zoneId) {
        return LocalDate.now(zoneId);
    }

    public static LocalDate dateAt(long timeMillis) {
        return dateAt(timeMillis, ZONE_DEFAULT);
    }

    public static LocalDate utcDateAt(long timeMillis) {
        return dateAt(timeMillis, UTC);
    }

    public static LocalDate dateAt(long timeMillis, ZoneId zoneId) {
        return dateTimeAt(timeMillis, zoneId).toLocalDate();
    }

    public static LocalDateTime dateTimeNow() {
        return LocalDateTime.now(ZONE_DEFAULT);
    }

    public static LocalDateTime utcDateTimeNow() {
        return dateTimeNow(UTC);
    }

    public static LocalDateTime dateTimeNow(ZoneId zoneId) {
        return LocalDateTime.now(zoneId);
    }

    public static LocalDateTime dateTimeAt(long timeMillis) {
        return dateTimeAt(timeMillis, ZONE_DEFAULT);
    }

    public static LocalDateTime utcDateTimeAt(long timeMillis) {
        return dateTimeAt(timeMillis, UTC);
    }

    public static LocalDateTime dateTimeAt(long timeMillis, ZoneId zoneId) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMillis), zoneId);
    }

    /**
     * 获取某一天开始的毫秒数.
     * <br/>2022-12-12 16:32:00 -> 2022-12-12 00:00:00,000 的毫秒数
     *
     * @param localDate
     * @return
     */
    public static long utcStartOfDayMillis(LocalDate localDate) {
        return startOfDayMillis(localDate, UTC);
    }

    public static long startOfDayMillis(LocalDate localDate) {
        return startOfDayMillis(localDate, ZONE_DEFAULT);
    }

    public static long startOfDayMillis(LocalDate localDate, ZoneId zoneId) {
        return localDate.atStartOfDay(zoneId).toEpochSecond() * 1_000L;
    }

    /**
     * 获取某一天结束的毫秒数.
     * <br/>2022-12-12 16:32:00 -> 2022-12-12 23:59:59,999 的毫秒数
     *
     * @param localDate
     * @return
     */
    public static long utcEndOfDaysMillis(LocalDate localDate) {
        return endOfDaysMillis(localDate, UTC);
    }

    public static long endOfDaysMillis(LocalDate localDate) {
        return endOfDaysMillis(localDate, ZONE_DEFAULT);
    }

    public static long endOfDaysMillis(LocalDate localDate, ZoneId zoneId) {
        return LocalDateTime.of(localDate, LocalTime.MAX).atZone(zoneId).toEpochSecond() * 1_000L;
    }


    /**
     * 将指定的字符串日期转化成java.util.Date类型<br>
     * <b>注意字符日期格式必须是：yyyy-MM-dd</b>
     */
    public static Date toDate(String source) {
        try {
            return java.text.DateFormat.getDateInstance().parse(source);
        } catch (ParseException e) {
            log.error(e.getMessage(), e);
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
            log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 将指定的字符串日期转化成java.util.Date类型，
     * 这里的字符日期格式必须可以是pattern参数指定的任何格式
     */
    public static Date toDateTime(String source, String pattern) {
        java.text.DateFormat df = new SimpleDateFormat(pattern);
        try {
            return df.parse(source);
        } catch (ParseException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

}
