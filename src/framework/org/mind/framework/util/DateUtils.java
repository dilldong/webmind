package org.mind.framework.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjuster;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;

/**
 * 对日期、时间格式化工具类
 *
 * @author dp
 */
@Slf4j
public class DateUtils {
    public static final ZoneId ZONE_DEFAULT = ZoneId.systemDefault();
    public static final ZoneId UTC = ZoneId.of("UTC");
    public static final ZoneId UTC8 = ZoneId.of("GMT+8");
    public static final TimeZone UTC_TIMEZONE = TimeZone.getTimeZone(UTC);
    public static final TimeZone UTC8_TIMEZONE = TimeZone.getTimeZone(UTC8);
    public static final long ONE_DAY_MILLIS = 86_400_000L;
    public static final long ONE_HOUR_MILLIS = 3_600_000L;

    public static final String FULL_DATE_PATTERN = "yyyyMMdd";
    public static final String SIMPLE_DATE_PATTERN = "yyMMdd";
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String ENS_DATE_PATTERN = "MM/dd/yyyy";
    public static final String TIME_PATTERN = "HH:mm:ss";
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 返回时间的毫秒数，同System.currentTimeMillis()结果一致.
     */
    public static long getMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 返回时间的秒数
     */
    public static long getSeconds() {
        return getMillis() / 1000L;
    }

    public static long getSeconds(long timemillis) {
        return timemillis / 1000L;
    }

    public static long getSeconds(LocalDateTime dateTime) {
        return getSeconds(dateTime, ZONE_DEFAULT);
    }

    public static long getSeconds(LocalDateTime dateTime, ZoneId zoneId) {
        return dateTime.atZone(zoneId).toEpochSecond();
    }

    public static long getSeconds(LocalDate localDate) {
        return getSeconds(localDate.atStartOfDay());
    }

    public static long getSeconds(LocalDate localDate, ZoneId zoneId) {
        return getSeconds(localDate.atStartOfDay(), zoneId);
    }

    /**
     * 返回某天之前的时间戳
     */
    public static long before(int days) {
        if (days < 1)
            return -1L;
        return getMillis() - days * ONE_DAY_MILLIS;
    }

    /**
     * 返回某天之前的起始时间戳
     */
    public static long beforeAtStartOfDay(int days) {
        if (days < 1)
            return -1L;
        return beforeAtStartOfDay(days, ZONE_DEFAULT);
    }

    /**
     * 指定时区, 返回某天之前的起始时间戳
     */
    public static long beforeAtStartOfDay(int days, ZoneId zoneId) {
        long timestamp = before(days);
        return timestamp == -1 ? timestamp : startOfDayMillis(dateAt(timestamp, zoneId), zoneId);
    }

    /**
     * 将指定特殊的格式，返回格式化的时间；
     * 如果不是特别的日期格式，请使用该类的其它方法.
     */
    public static String format(Date date, String pattern) {
        if (Objects.isNull(date))
            date = currentDate();

        if (StringUtils.isEmpty(pattern))
            return formatDateTime(date);

        return DateFormatUtils.format(date, pattern);
    }

    public static String format(Date date, String pattern, TimeZone zone) {
        if (Objects.isNull(date))
            date = currentDate();

        if (StringUtils.isEmpty(pattern))
            return formatDateTime(date, zone);

        return DateFormatUtils.format(date, pattern, zone);
    }

    public static String formatUTC(Date date, String pattern) {
        return DateFormatUtils.formatUTC(date, pattern);
    }

    /**
     * 需要指定日期，如果该值为null，将返回当前日期下的时间值，
     * 格式类型是：yyyy-MM-dd HH:mm:ss
     */
    public static String format(Date date) {
        return format(date, null);
    }

    public static String format(long timemillis, String pattern) {
        return DateFormatUtils.format(timemillis, pattern);
    }

    public static String format(long timemillis, String pattern, TimeZone zone) {
        return DateFormatUtils.format(timemillis, pattern, zone);
    }

    public static String formatUTC(long timemillis, String pattern) {
        return DateFormatUtils.formatUTC(timemillis, pattern);
    }

    public static String formatUTC(Date date) {
        return formatUTC(date, DATE_TIME_PATTERN);
    }

    public static String formatDate() {
        return formatDate(currentDate());
    }

    public static String formatDate(TimeZone zone) {
        return formatDate(currentDate(), zone);
    }

    public static String formatDate(Date date) {
        return DateFormat.getDateInstance().format(date);
    }

    public static String formatDate(Date date, TimeZone zone) {
        DateFormat df = DateFormat.getDateInstance();
        df.setTimeZone(zone);
        return df.format(date);
    }

    public static String formatDate(long timemillis) {
        return formatDate(new Date(timemillis));
    }

    public static String formatDate(long timemillis, TimeZone zone) {
        return formatDate(new Date(timemillis), zone);
    }

    public static String formatTime() {
        return formatTime(currentDate());
    }

    public static String formatTime(Date date) {
        return DateFormat.getTimeInstance().format(date);
    }

    public static String formatTime(long timemillis) {
        return DateFormatUtils.format(timemillis, TIME_PATTERN);
    }

    public static String formatDateTime() {
        return formatDateTime(currentDate());
    }

    public static String formatDateTime(TimeZone zone) {
        return formatDateTime(currentDate(), zone);
    }

    public static String formatDateTime(Date date) {
        return DateFormat.getDateTimeInstance().format(date);
    }

    public static String formatDateTime(Date date, TimeZone zone) {
        DateFormat df = DateFormat.getDateTimeInstance();
        df.setTimeZone(zone);
        return df.format(date);
    }

    public static String formatDateTime(long timemillis) {
        return formatDateTime(new Date(timemillis));
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
     */
    public static long utcStartOfDayMillis(LocalDate localDate) {
        return startOfDayMillis(localDate, UTC);
    }

    public static long utcStartOfDaySeconds(LocalDate localDate) {
        return startOfDaySeconds(localDate, UTC);
    }

    public static long utcStartOfDaySeconds(Date date) {
        return startOfDaySeconds(date, UTC);
    }


    public static long startOfDayMillis(LocalDate localDate) {
        return startOfDayMillis(localDate, ZONE_DEFAULT);
    }

    public static long startOfDaySeconds(Date date) {
        return startOfDaySeconds(dateAt(date.getTime()));
    }

    public static long startOfDaySeconds(Date date, ZoneId zoneId) {
        return startOfDaySeconds(dateAt(date.getTime(), zoneId), zoneId);
    }

    public static long startOfDaySeconds(LocalDate localDate) {
        return startOfDaySeconds(localDate, ZONE_DEFAULT);
    }

    public static long startOfDaySeconds(LocalDateTime localDateTime) {
        return startOfDaySeconds(localDateTime, ZONE_DEFAULT);
    }

    public static long startOfDayMillis(LocalDateTime localDateTime) {
        return startOfDayMillis(localDateTime, ZONE_DEFAULT);
    }

    public static long startOfDayMillis(LocalDate localDate, ZoneId zoneId) {
        return localDate.atStartOfDay(zoneId).toInstant().toEpochMilli();
    }

    public static long startOfDayMillis(LocalDateTime localDateTime, ZoneId zoneId) {
        return localDateTime.atZone(zoneId).toInstant().toEpochMilli();
    }

    public static long startOfDaySeconds(LocalDate localDate, ZoneId zoneId) {
        return localDate.atStartOfDay(zoneId).toEpochSecond();
    }

    public static long startOfDaySeconds(LocalDateTime localDateTime, ZoneId zoneId) {
        return localDateTime.atZone(zoneId).toInstant().getEpochSecond();
    }


    /**
     * 获取某一天结束的毫秒数.
     * <br/>2022-12-12 16:32:00 -> 2022-12-12 23:59:59,999 的毫秒数
     */
    public static long utcEndOfDayMillis(LocalDate localDate) {
        return endOfDayMillis(localDate, UTC);
    }

    public static long utcEndOfDaySeconds(LocalDate localDate) {
        return endOfDaySeconds(localDate, UTC);
    }

    public static long utcEndOfDaySeconds(Date date) {
        return endOfDaySeconds(date, UTC);
    }

    public static long endOfDayMillis(LocalDate localDate) {
        return endOfDayMillis(localDate, ZONE_DEFAULT);
    }

    public static long endOfDayMillis(LocalDateTime localDateTime) {
        return endOfDayMillis(localDateTime, ZONE_DEFAULT);
    }

    public static long endOfDaySeconds(LocalDate localDate) {
        return endOfDaySeconds(localDate, ZONE_DEFAULT);
    }

    public static long endOfDaySeconds(LocalDateTime localDateTime) {
        return endOfDaySeconds(localDateTime, ZONE_DEFAULT);
    }

    public static long endOfDaySeconds(Date date) {
        return endOfDaySeconds(dateAt(date.getTime()));
    }

    public static long endOfDaySeconds(Date date, ZoneId zoneId) {
        return endOfDaySeconds(dateAt(date.getTime(), zoneId), zoneId);
    }

    public static long endOfDayMillis(LocalDate localDate, ZoneId zoneId) {
        return LocalDateTime.of(localDate, LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli();
    }

    public static long endOfDayMillis(LocalDateTime localDateTime, ZoneId zoneId) {
        return localDateTime.with(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli();
    }

    public static long endOfDaySeconds(LocalDate localDate, ZoneId zoneId) {
        return LocalDateTime.of(localDate, LocalTime.MAX).atZone(zoneId).toEpochSecond();
    }

    public static long endOfDaySeconds(LocalDateTime localDateTime, ZoneId zoneId) {
        return localDateTime.with(LocalTime.MAX).atZone(zoneId).toEpochSecond();
    }

    /**
     * 计算两个时间之差
     */
    public static Duration endOfRemaining(LocalDateTime start, TemporalAdjuster endOf){
        LocalDateTime nextMidnight = start.with(endOf).plusDays(1L);
        return Duration.between(start, nextMidnight);
    }

    /**
     * 将指定的字符串日期转化成java.util.Date类型
     */
    public static Date parseDate(String source) {
        try {
            return DateFormat.getDateInstance().parse(source);
        } catch (ParseException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 将指定的字符串日期转化成java.util.Date类型
     */
    public static Date parseDateTime(String source) {
        try {
            return DateFormat.getDateTimeInstance().parse(source);
        } catch (ParseException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 将指定的字符串日期转化成java.util.Date类型，
     * 这里的字符日期格式必须可以是pattern参数指定的任何格式
     */
    public static Date parse(String source, String pattern) {
        try {
            return org.apache.commons.lang3.time.DateUtils.parseDate(source, pattern);
        } catch (ParseException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 将LocalDateTime的时间转成所在的分钟数:
     * e.g: 2022.10.08 10:03:42.938 -> 2022.10.08 10:03:00 的毫秒数
     *
     * @return 到分钟的毫秒数
     */
    public static long ofMinutes(LocalDateTime localTime, ZoneId zone) {
        long seconds = localTime.atZone(zone).toEpochSecond();
        return (seconds - localTime.getSecond()) * 1_000L;
    }

    public static long ofMinutes(long timestamp) {
        return ofMinutes(utcDateTimeAt(timestamp), UTC);
    }

    public static long ofMinutes(Date date) {
        return ofMinutes(date.getTime());
    }

    /**
     * 将LocalDateTime的时间转成所在的小时数:
     * e.g: 2022.10.08 10:03:42.938 -> 2022.10.08 10:00:00 的毫秒数
     *
     * @return 到小时的毫秒数
     */
    public static long ofHours(LocalDateTime localTime, ZoneId zone) {
        long timemillis = ofMinutes(localTime, zone);
        return timemillis - localTime.getMinute() * 60L * 1_000L;
    }

    public static long ofHours(long timestamp) {
        return ofHours(utcDateTimeAt(timestamp), UTC);
    }

    public static long ofHours(Date date) {
        return ofHours(date.getTime());
    }
}
