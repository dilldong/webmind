package org.mind.framework.metric;

/**
 * @author Marcus
 * @version 1.0
 */
public class BaseTraffic {
    protected final transient Metric metric;

    public BaseTraffic(Metric metric) {
        this.metric = metric;
    }

    public void recordSuccess(long requestTime) {
        recordSuccess();
        metric.addRt(requestTime);
    }

    public void recordSuccess() {
        metric.addSuccess(1);
    }


    public void recordException() {
        metric.addException(1);
    }

    public long totalSuccess() {
        return metric.success();
    }

    public long totalException() {
        return metric.exception();
    }

    public long totalRt() {
        return metric.rt();
    }

    public long minRt() {
        return metric.minRt();
    }

    public long maxRt() {
        return metric.maxRt();
    }

    public long totalRequestByPeriod() {
        return metric.countByPeriod();
    }
}
