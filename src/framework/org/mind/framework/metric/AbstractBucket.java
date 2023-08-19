package org.mind.framework.metric;

import lombok.extern.slf4j.Slf4j;
import org.mind.framework.util.DateUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Marcus
 * @version 1.0
 */
@Slf4j
public abstract class AbstractBucket {
    private final int duration;
    private final int bucketSize;
    private final int intervalInMillis;
    private final AtomicReferenceArray<Indicator> indicatorArray;

    private transient final Lock lock = new ReentrantLock();

    protected abstract Indicator newIndicator(long duration, long startTime);

    protected abstract Indicator resetIndicator(Indicator old, long startTime);

    public AbstractBucket(int bucketSize, int intervalInMillis) {
        this.duration = intervalInMillis / bucketSize;
        this.intervalInMillis = intervalInMillis;
        this.bucketSize = bucketSize;
        indicatorArray = new AtomicReferenceArray<>(bucketSize);
    }

    public Indicator current() {
        return current(DateUtils.getMillis());
    }

    public Indicator current(long currTimeMillis) {
        if (currTimeMillis < 0)
            return null;

        int bucketIndex = getBucketIndex(currTimeMillis);
        long startTime = startTimeInBucket(currTimeMillis);

        // Get the Indicator at the specified time from the AtomicReferenceArray
        while (true) {
            Indicator old = indicatorArray.get(bucketIndex);
            if (Objects.isNull(old)) {
                Indicator newIndicator = newIndicator(duration, startTime);
                if (indicatorArray.compareAndSet(bucketIndex, null, newIndicator))
                    return newIndicator;

                // yield CPU scheduling
                Thread.yield();
            } else if (startTime == old.getStartTimeOfBucket()) {// the same time period
                return old;
            } else if (startTime > old.getStartTimeOfBucket()) {// the next time period
                if (lock.tryLock()) {
                    try {
                        return resetIndicator(old, startTime);
                    } finally {
                        lock.unlock();
                    }
                }

                Thread.yield();
            } else
                return newIndicator(duration, startTime);// time won't go back
        }
    }


    public List<Indicator> getValuesByPeriod() {
        return getValuesByPeriod(DateUtils.getMillis());
    }

    public List<Indicator> getValuesByPeriod(long timeMillis) {
        if (timeMillis < 0)
            return Collections.emptyList();

        final int size = indicatorArray.length();
        List<Indicator> resultList = new ArrayList<>(size);
        for (int i = 0; i < size; ++i) {
            Indicator indicator = indicatorArray.get(i);
            if (indicator == null || isDiscard(timeMillis, indicator))
                continue;

            resultList.add(indicator);
        }

        return resultList;
    }

    // Discard after intervalInMillis time
    private boolean isDiscard(long timeMillis, Indicator indicator) {
        return timeMillis - indicator.getStartTimeOfBucket() > intervalInMillis;
    }

    private int getBucketIndex(long currMillis) {
        long timeId = currMillis / duration;
        return (int) (timeId % bucketSize);
    }

    private long startTimeInBucket(long currMillis) {
        return currMillis - currMillis % duration;
    }
}
