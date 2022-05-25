package org.mind.framework.metric;

/**
 * Metric event enum
 */
public enum MetricEvent {
    SUCCESS, EXCEPTION, TIME;

    public static int size() {
        return MetricEvent.values().length;
    }
}
