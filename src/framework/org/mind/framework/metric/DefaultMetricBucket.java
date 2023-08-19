package org.mind.framework.metric;

/**
 * @author Marcus
 * @version 1.0
 * @date 2022-05-24
 */
public class DefaultMetricBucket extends AbstractBucket {

    public DefaultMetricBucket(int bucketSize, int intervalInMillis) {
        super(bucketSize, intervalInMillis);
    }

    @Override
    protected Indicator newIndicator(long duration, long startTime) {
        return new Indicator(duration, startTime);
    }

    @Override
    protected Indicator resetIndicator(Indicator old, long startTime) {
        return old.reset(startTime);
    }
}
