package org.mind.framework.web.server.tomcat.monitor;

import jakarta.servlet.ServletException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.util.DateUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Tomcat Monitor
 *
 * @version 1.0
 * @author Marcus
 * @date 2025/5/27
 */
@Slf4j(topic = "ServerMonitor")
public class MonitoringValve extends ValveBase {
    // 统计信息
    private static final AtomicLong REQUEST_COUNT = new AtomicLong(0);
    private static final AtomicLong ERROR_COUNT = new AtomicLong(0);
    private static final LongAdder TOTAL_RESPONSE_TIME = new LongAdder();
    private static final AtomicLong MAX_RESPONSE_TIME = new AtomicLong(0);
    private static final AtomicLong MIN_RESPONSE_TIME = new AtomicLong(Long.MAX_VALUE);

    // 状态码统计
    private static final AtomicLong STATUS_2XX = new AtomicLong(0);
    private static final AtomicLong STATUS_3XX = new AtomicLong(0);
    private static final AtomicLong STATUS_4XX = new AtomicLong(0);
    private static final AtomicLong STATUS_5XX = new AtomicLong(0);

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        long startTime = DateUtils.CachedTime.currentMillis();
        long currentRequestId = REQUEST_COUNT.incrementAndGet();

        try {
            // 调用下一个Valve或Servlet
            getNext().invoke(request, response);

        } catch (IOException | ServletException | RuntimeException e) {
            ERROR_COUNT.incrementAndGet();
            log.error("Request handle exception #{} - {}: {}", currentRequestId, e.getClass().getSimpleName(), e.getMessage());
            throw e;
        } finally {
            // 计算响应时间
            long responseTime = DateUtils.CachedTime.currentMillis() - startTime;

            // 更新统计信息
            updateStatistics(responseTime, response.getStatus());
        }
    }

    /**
     * 更新统计信息
     */
    private void updateStatistics(long responseTime, int status) {
        // 响应时间统计
        TOTAL_RESPONSE_TIME.add(responseTime);

        // 更新最大响应时间
        long currentMax = MAX_RESPONSE_TIME.get();
        while (responseTime > currentMax && !MAX_RESPONSE_TIME.compareAndSet(currentMax, responseTime)) {
            currentMax = MAX_RESPONSE_TIME.get();
        }

        // 更新最小响应时间
        long currentMin = MIN_RESPONSE_TIME.get();
        while (responseTime < currentMin && !MIN_RESPONSE_TIME.compareAndSet(currentMin, responseTime)) {
            currentMin = MIN_RESPONSE_TIME.get();
        }

        // 状态码统计
        if (status >= 200 && status < 300) {
            STATUS_2XX.incrementAndGet();
        } else if (status >= 300 && status < 400) {
            STATUS_3XX.incrementAndGet();
        } else if (status >= 400 && status < 500) {
            STATUS_4XX.incrementAndGet();
        } else if (status >= 500) {
            STATUS_5XX.incrementAndGet();
        }
    }

    /**
     * 获取统计信息摘要
     */
    public String getStatisticsSummary() {
        long totalRequests = REQUEST_COUNT.get();
        if (totalRequests == 0)
            return StringUtils.EMPTY;

        long avgResponseTime = TOTAL_RESPONSE_TIME.sum() / totalRequests;
        double errorRate = (double) ERROR_COUNT.get() / totalRequests * 100;

        return String.format(
                "Request Summary - Total: %d, Error: %d (%.2f%%), " +
                        "Response - avg: %dms, min: %dms, max: %dms, " +
                        "Status - 2xx: %d, 3xx: %d, 4xx: %d, 5xx: %d",
                totalRequests, ERROR_COUNT.get(), errorRate,
                avgResponseTime, MIN_RESPONSE_TIME.get() == Long.MAX_VALUE ? 0 : MIN_RESPONSE_TIME.get(), MAX_RESPONSE_TIME.get(),
                STATUS_2XX.get(), STATUS_3XX.get(), STATUS_4XX.get(), STATUS_5XX.get()
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        REQUEST_COUNT.set(0);
        ERROR_COUNT.set(0);
        TOTAL_RESPONSE_TIME.reset();
        MAX_RESPONSE_TIME.set(0);
        MIN_RESPONSE_TIME.set(Long.MAX_VALUE);
        STATUS_2XX.set(0);
        STATUS_3XX.set(0);
        STATUS_4XX.set(0);
        STATUS_5XX.set(0);

        log.info("Monitoring statistics have been reset");
    }
}
