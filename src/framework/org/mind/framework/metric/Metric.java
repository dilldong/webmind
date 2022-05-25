package org.mind.framework.metric;

public interface Metric {

    /**
     * Total number of success
     */
    long success();

    /**
     * Total number of exceptions
     */
    long exception();

    /**
     * Total request time
     */
    long rt();

    /**
     * Minimum request time
     */
    long minRt();

    /**
     * Maximum request time
     *
     * @return
     */
    long maxRt();

    /**
     * Add count to successes
     *
     * @param count count to add
     */
    void addSuccess(int count);

    /**
     * Add count to exceptions
     */
    void addException(int count);

    /**
     * Add count to request-time
     *
     * @param requestTime RT
     */
    void addRt(long requestTime);

    /**
     * The total number of requests, such as statistics per second QPS.
     * the total number of normal and successes and exceptions
     *
     * @return
     */
    long countByPeriod();
}
