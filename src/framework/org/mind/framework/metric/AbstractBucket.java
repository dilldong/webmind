package org.mind.framework.metric;

import lombok.extern.slf4j.Slf4j;
import org.mind.framework.util.DateFormatUtils;

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
    private final int intervalInMills;
    private final AtomicReferenceArray<Indicator> indicatorArray;

    private transient final Lock lock = new ReentrantLock();

    protected abstract Indicator newIndicator(long duration, long startTime);

    protected abstract Indicator resetIndicator(Indicator old, long startTime);

    public AbstractBucket(int bucketSize, int intervalInMills) {
        this.duration = intervalInMills / bucketSize;
        this.intervalInMills = intervalInMills;
        this.bucketSize = bucketSize;
        indicatorArray = new AtomicReferenceArray<>(bucketSize);
    }

    public Indicator current() {
        return current(DateFormatUtils.getMillis());
    }

    public Indicator current(long currTimeMills) {
        if (currTimeMills < 0)
            return null;

        int bucketIndex = getBucketIndex(currTimeMills);
        long startTime = startTimeInBucket(currTimeMills);

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
        return getValuesByPeriod(DateFormatUtils.getMillis());
    }

    public List<Indicator> getValuesByPeriod(long timeMills) {
        if (timeMills < 0)
            return Collections.EMPTY_LIST;

        final int size = indicatorArray.length();
        List<Indicator> resultList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Indicator indicator = indicatorArray.get(i);
            if (indicator == null || isDiscard(timeMills, indicator))
                continue;

            resultList.add(indicator);
        }

        return resultList;
    }

    // Discard after intervalInMills time
    private boolean isDiscard(long timeMills, Indicator indicator) {
        return timeMills - indicator.getStartTimeOfBucket() > intervalInMills;
    }

    private int getBucketIndex(long currMills) {
        long timeId = currMills / duration;
        return (int) (timeId % bucketSize);
    }

    private long startTimeInBucket(long currMills) {
        return currMills - currMills % duration;
    }
}
