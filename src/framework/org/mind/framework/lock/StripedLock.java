package org.mind.framework.lock;

import org.mind.framework.web.Destroyable;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @version 1.0
 * @author Marcus
 * @date 2025/2/27
 */
public class StripedLock implements Destroyable {
    private final Lock[] locks;  // 锁分段数组
    private final int stripes;   // 分段数量

    /**
     * 创建指定分段数量的锁
     *
     * @param stripes 分段数量（建议为2的幂次，如16/32/64）
     */
    public StripedLock(int stripes) {
        this.stripes = stripes;
        this.locks = new Lock[stripes];
        for (int i = 0; i < stripes; ++i) {
            locks[i] = new ReentrantLock();
        }
    }

    /**
     * 根据键的哈希值获取对应的锁
     *
     * @param key 需要加锁的键（如行号、ID等）
     */
    public Lock getLock(Object key) {
        // 通过哈希值取模确定锁分段
        int index = Math.abs(key.hashCode() % stripes);
        return getLock(index);
    }


    public Lock getLock(int segment) {
        return locks[segment];
    }

    @Override
    public void destroy() {
        for (int i = 0; i < stripes; ++i) {
            if (((ReentrantLock) locks[i]).isLocked())
                locks[i].unlock();
            locks[i] = null;
        }
    }
}
