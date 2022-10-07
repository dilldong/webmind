package org.mind.framework.metric;

import org.mind.framework.util.DateFormatUtils;

/**
 * @author Marcus
 * @version 1.0
 * @date 2022-05-24
 */
public class MinuteTraffic extends BaseTraffic {
    /**
     * The last 60 seconds of statistics
     * 60 * 1000: One bucket per second
     */
    public MinuteTraffic() {
        super(new DefaultMetric(60, 60 * 1000));
    }

    public long getRequestCurrent() {
        final Indicator indicator = ((DefaultMetric) metric).getBucket().current(DateFormatUtils.getMillis());
        return indicator.success() + indicator.exception();
    }

}
