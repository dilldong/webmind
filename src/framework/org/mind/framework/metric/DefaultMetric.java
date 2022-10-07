package org.mind.framework.metric;

import lombok.Getter;
import org.mind.framework.util.DateFormatUtils;

import java.util.Comparator;

/**
 * @author Marcus
 * @version 1.0
 * @date 2022-05-24
 */
public class DefaultMetric implements Metric {

    @Getter
    private final AbstractBucket bucket;

    public DefaultMetric(int bucketSizem, int intervalInMills) {
        this.bucket = new DefaultMetricBucket(bucketSizem, intervalInMills);
    }

    @Override
    public long success() {
        return success(DateFormatUtils.getMillis());
    }

    public long success(long timeMills) {
        // Make sure the bucket at the current time is not empty
        bucket.current(timeMills);

        return
                bucket.getValuesByPeriod(timeMills)
                        .parallelStream()
                        .mapToLong(Indicator::success)
                        .sum();
    }

    @Override
    public long exception() {
        return exception(DateFormatUtils.getMillis());
    }

    public long exception(long timeMills) {
        // Make sure the bucket at the current time is not empty
        bucket.current(timeMills);

        return
                bucket.getValuesByPeriod(timeMills)
                        .parallelStream()
                        .mapToLong(Indicator::exception)
                        .sum();
    }

    @Override
    public long rt() {
        // Make sure the bucket at the current time is not empty
        long timeMills = DateFormatUtils.getMillis();
        bucket.current(timeMills);

        return
                bucket.getValuesByPeriod(timeMills)
                        .parallelStream()
                        .mapToLong(Indicator::time)
                        .sum();
    }

    @Override
    public long minRt() {
        // Make sure the bucket at the current time is not empty
        long timeMills = DateFormatUtils.getMillis();
        bucket.current(timeMills);
        return
                bucket.getValuesByPeriod(timeMills)
                        .parallelStream()
                        .min(Comparator.comparing(Indicator::minTime))
                        .get()
                        .minTime();
    }

    @Override
    public long maxRt() {
        // Make sure the bucket at the current time is not empty
        long timeMills = DateFormatUtils.getMillis();
        bucket.current(timeMills);

        return
                bucket.getValuesByPeriod(timeMills)
                        .parallelStream()
                        .max(Comparator.comparing(Indicator::maxTime))
                        .get()
                        .maxTime();
    }

    @Override
    public void addSuccess(int count) {
        bucket.current().addSuccess(count);
    }

    @Override
    public void addException(int count) {
        bucket.current().addException(count);
    }

    @Override
    public void addRt(long requestTime) {
        bucket.current().addRequestTime(requestTime);
    }

    @Override
    public long countByPeriod() {
        final long time = DateFormatUtils.getMillis();
        return success(time) + exception(time);
    }
}
