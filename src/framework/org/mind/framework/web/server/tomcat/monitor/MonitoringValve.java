package org.mind.framework.web.server.tomcat.monitor;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.util.DateUtils;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Tomcat Monitor
 *
 * @version 1.0
 * @auther Marcus
 * @date 2025/5/27
 */
@Slf4j(topic = "ServerMonitor")
public class MonitoringValve extends ValveBase {
    // 统计信息
    private static final AtomicLong requestCount = new AtomicLong(0);
    private static final AtomicLong errorCount = new AtomicLong(0);
    private static final LongAdder totalResponseTime = new LongAdder();
    private static final AtomicLong maxResponseTime = new AtomicLong(0);
    private static final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);

    // 状态码统计
    private static final AtomicLong status2xx = new AtomicLong(0);
    private static final AtomicLong status3xx = new AtomicLong(0);
    private static final AtomicLong status4xx = new AtomicLong(0);
    private static final AtomicLong status5xx = new AtomicLong(0);

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        long startTime = DateUtils.CachedTime.currentMillis();
        long currentRequestId = requestCount.incrementAndGet();

        try {
            // 调用下一个Valve或Servlet
            getNext().invoke(request, response);

        } catch (IOException | ServletException | RuntimeException e) {
            errorCount.incrementAndGet();
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
        totalResponseTime.add(responseTime);

        // 更新最大响应时间
        long currentMax = maxResponseTime.get();
        while (responseTime > currentMax && !maxResponseTime.compareAndSet(currentMax, responseTime)) {
            currentMax = maxResponseTime.get();
        }

        // 更新最小响应时间
        long currentMin = minResponseTime.get();
        while (responseTime < currentMin && !minResponseTime.compareAndSet(currentMin, responseTime)) {
            currentMin = minResponseTime.get();
        }

        // 状态码统计
        if (status >= 200 && status < 300) {
            status2xx.incrementAndGet();
        } else if (status >= 300 && status < 400) {
            status3xx.incrementAndGet();
        } else if (status >= 400 && status < 500) {
            status4xx.incrementAndGet();
        } else if (status >= 500) {
            status5xx.incrementAndGet();
        }
    }

    /**
     * 获取统计信息摘要
     */
    public String getStatisticsSummary() {
        long totalRequests = requestCount.get();
        if (totalRequests == 0)
            return StringUtils.EMPTY;

        long avgResponseTime = totalResponseTime.sum() / totalRequests;
        double errorRate = (double) errorCount.get() / totalRequests * 100;

        return String.format(
                "Request Summary - Total: %d, Error: %d (%.2f%%), " +
                        "Response - avg: %dms, min: %dms, max: %dms, " +
                        "Status - 2xx: %d, 3xx: %d, 4xx: %d, 5xx: %d",
                totalRequests, errorCount.get(), errorRate,
                avgResponseTime, minResponseTime.get() == Long.MAX_VALUE ? 0 : minResponseTime.get(), maxResponseTime.get(),
                status2xx.get(), status3xx.get(), status4xx.get(), status5xx.get()
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        requestCount.set(0);
        errorCount.set(0);
        totalResponseTime.reset();
        maxResponseTime.set(0);
        minResponseTime.set(Long.MAX_VALUE);
        status2xx.set(0);
        status3xx.set(0);
        status4xx.set(0);
        status5xx.set(0);

        log.info("Monitoring statistics have been reset");
    }
}
