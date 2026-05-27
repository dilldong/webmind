package org.mind.framework.service.threads;

import org.mind.framework.service.queue.DelegateMessage;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 捕获当前 MDC 快照，在子线程中恢复
 *
 * @author: Marcus
 * @date: 2026/5/21
 * @version: 1.0
 */
public class ThreadContextPropagator {

    /**
     * 包装 Runnable
     */
    public static Runnable wrap(Runnable task) {
        Map<String, String> context = capture();
        return () -> restore(context, task);
    }

    /**
     * 包装 Callable
     */
    public static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, String> context = capture();
        return () -> restoreAndGet(context, () -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 包装 Supplier
     */
    public static <T> Supplier<T> wrap(Supplier<T> task) {
        Map<String, String> context = capture();
        return () -> restoreAndGet(context, task);
    }

    /**
     * 包装 Function
     */
    public static <T, R> Function<T, R> wrap(Function<T, R> task) {
        Map<String, String> context = capture();
        return input -> restoreAndGet(context, () -> task.apply(input));
    }

    /**
     * 捕获当前线程的 MDC 快照
     */
    public static Map<String, String> capture() {
        Map<String, String> ctx = MDC.getCopyOfContextMap();
        return Objects.nonNull(ctx) ? ctx : Collections.emptyMap();
    }

    /**
     * 包装 DelegateMessage
     */
    public static DelegateMessage wrap(DelegateMessage task) {
        Map<String, String> context = capture();
        return () -> restore(context, task::process);
    }

    /**
     * 恢复 MDC，执行完后清理
     * Runnable 版
     */
    public static void restore(Map<String, String> context, Runnable task) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        MDC.setContextMap(context);

        try {
            task.run();
        } finally {
            if (Objects.nonNull(previous)) {
                MDC.setContextMap(previous);
            } else {
                MDC.clear();
            }
        }
    }

    /**
     * 恢复 MDC，执行完后清理
     * Supplier 版
     */
    public static <T> T restoreAndGet(Map<String, String> context, Supplier<T> task) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        MDC.setContextMap(context);

        try {
            return task.get();
        } finally {
            if (Objects.nonNull(previous)) {
                MDC.setContextMap(previous);
            } else {
                MDC.clear();
            }
        }
    }


}