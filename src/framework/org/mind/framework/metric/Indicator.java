package org.mind.framework.metric;

import lombok.Getter;

import java.util.concurrent.atomic.LongAdder;

/**
 * @author Marcus
 * @version 1.0
 */
public class Indicator {
    /**
     * Store counts for each event
     */
    private final LongAdder[] counters;

    /**
     * Minimum time spent during this period
     */
    private volatile long minTime = Long.MAX_VALUE;

    /**
     * Maximum time spent during this period
     */
    private volatile long maxTime = Long.MIN_VALUE;

    /**
     * Of time during this period
     */
    @Getter
    private final long duration;

    /**
     * Start timestamp of bucket (milliseconds)
     */
    @Getter
    private long startTimeOfBucket;

    public Indicator(long duration, long startMillis) {
        this.duration = duration;
        this.startTimeOfBucket = startMillis;

        int size = MetricEvent.size();
        this.counters = new LongAdder[size];
        for (int i = 0; i < size; ++i)
            counters[i] = new LongAdder();
    }

    public Indicator reset(long startMillis) {
        this.startTimeOfBucket = startMillis;

        int size = MetricEvent.size();
        for (int i = 0; i < size; ++i)
            counters[i].reset();

        return this;
    }

    public long success() {
        return counters[MetricEvent.SUCCESS.ordinal()].sum();
    }

    public long exception() {
        return counters[MetricEvent.EXCEPTION.ordinal()].sum();
    }

    public long time() {
        return counters[MetricEvent.TIME.ordinal()].sum();
    }

    public long minTime() {
        return time() == 0L ? 0L : minTime;
    }

    public long maxTime() {
        return time() == 0L ? 0L : maxTime;
    }

    public void addException(int num) {
        add(MetricEvent.EXCEPTION, num);
    }

    public void addSuccess(int num) {
        add(MetricEvent.SUCCESS, num);
    }

    public void addRequestTime(long time) {
        add(MetricEvent.TIME, time);

        if (time < minTime)
            minTime = time;

        if (time > maxTime)
            maxTime = time;
    }

    private void add(MetricEvent event, long n) {
        counters[event.ordinal()].add(n);
    }


}
