package org.mind.framework.service.threads;

import org.apache.logging.log4j.ThreadContext;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * 捕获当前 ThreadContext 快照，在子线程中恢复
 *
 * @author: Marcus
 * @date: 2026/5/21
 * @version: 1.0
 */
public class ThreadContextPropagator {
    public static Map<String, String> capture() {
        return ThreadContext.getImmutableContext(); // 快照，非引用
    }

    public static void restore(Map<String, String> context) {
        ThreadContext.clearAll();
        if (Objects.nonNull(context)) {
            ThreadContext.putAll(context);
        }
    }

    public static void clear() {
        ThreadContext.clearAll();
    }

    /**
     * 包装 Runnable
     */
    public static Runnable wrap(Runnable task) {
        Map<String, String> context = capture();
        return () -> {
            restore(context);
            try {
                task.run();
            } finally {
                clear(); // 防止线程池线程上下文污染
            }
        };
    }

    /**
     * 包装 Callable
     */
    public static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, String> context = capture();
        return () -> {
            restore(context);
            try {
                return task.call();
            } finally {
                clear();
            }
        };
    }

}
