package org.mind.framework.metric;

import lombok.Getter;
import org.mind.framework.util.DateUtils;

import java.util.Comparator;

/**
 * @author Marcus
 * @version 1.0
 * @date 2022-05-24
 */
public class DefaultMetric implements Metric {

    @Getter
    private final AbstractBucket bucket;

    public DefaultMetric(int bucketSize, int intervalInMillis) {
        this.bucket = new DefaultMetricBucket(bucketSize, intervalInMillis);
    }

    @Override
    public long success() {
        return success(DateUtils.getMillis());
    }

    public long success(long timeMillis) {
        // Make sure the bucket at the current time is not empty
        bucket.current(timeMillis);

        return
                bucket.getValuesByPeriod(timeMillis)
                        .parallelStream()
                        .mapToLong(Indicator::success)
                        .sum();
    }

    @Override
    public long exception() {
        return exception(DateUtils.getMillis());
    }

    public long exception(long timeMillis) {
        // Make sure the bucket at the current time is not empty
        bucket.current(timeMillis);

        return
                bucket.getValuesByPeriod(timeMillis)
                        .parallelStream()
                        .mapToLong(Indicator::exception)
                        .sum();
    }

    @Override
    public long rt() {
        // Make sure the bucket at the current time is not empty
        long timeMillis = DateUtils.getMillis();
        bucket.current(timeMillis);

        return
                bucket.getValuesByPeriod(timeMillis)
                        .parallelStream()
                        .mapToLong(Indicator::time)
                        .sum();
    }

    @Override
    public long minRt() {
        // Make sure the bucket at the current time is not empty
        long timeMillis = DateUtils.getMillis();
        bucket.current(timeMillis);
        return
                bucket.getValuesByPeriod(timeMillis)
                        .parallelStream()
                        .min(Comparator.comparing(Indicator::minTime))
                        .get()
                        .minTime();
    }

    @Override
    public long maxRt() {
        // Make sure the bucket at the current time is not empty
        long timeMillis = DateUtils.getMillis();
        bucket.current(timeMillis);

        return
                bucket.getValuesByPeriod(timeMillis)
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
        final long time = DateUtils.getMillis();
        return success(time) + exception(time);
    }
}
