package org.mind.framework.lock;

import org.mind.framework.web.Destroyable;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2025/2/27
 */
public class StripedSynchronizedLock implements Destroyable {
    private final Object[] segments;  // 锁分段对象数组
    private final int stripes;        // 分段数量

    public StripedSynchronizedLock(int stripes) {
        this.stripes = stripes;
        this.segments = new Object[stripes];
        for (int i = 0; i < stripes; ++i) {
            segments[i] = new Object();
        }
    }

    /**
     * 根据键的哈希值获取对应的锁对象
     */
    public Object getSegmentLock(Object key) {
        int index = Math.abs(key.hashCode() % stripes);
        return getSegmentLock(index);
    }

    public Object getSegmentLock(int segment) {
        return segments[segment];
    }

    @Override
    public void destroy() {
        for (int i = 0; i < stripes; ++i) {
            segments[i] = null;
        }
    }
}
