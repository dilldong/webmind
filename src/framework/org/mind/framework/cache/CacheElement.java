package org.mind.framework.cache;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.service.Cloneable;
import org.mind.framework.util.DateUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cached wrapper object
 *
 * @author dp
 * @date Nov 26, 2010
 */
public class CacheElement implements Serializable {

    // cached object
    @Setter
    private volatile Object value;

    // The time recorded during the cache, if accessed, the last time will be recorded
    @Setter
    @Getter
    private volatile long lastTime;

    @Getter
    private final long firstTime;

    // hit rate
    private final AtomicInteger visited = new AtomicInteger(0);

    @Getter
    private final String key;

    @Setter
    @Getter
    private volatile long ttlMillis;

    public CacheElement(Object data, String key, long time, long ttlMillis, Cloneable.CloneType type) {
        this.firstTime = time;
        this.lastTime = time;
        this.key = key;
        this.ttlMillis = ttlMillis;

        switch (type) {
            case NONE:
                this.value = data;
                break;
            case CLONE:
                this.value = this.cloneValue(data);
                break;
        }
    }

    public CacheElement(Object data, String key, long ttlMillis, Cloneable.CloneType type) {
        this(data, key, DateUtils.CachedTime.currentMillis(), ttlMillis, type);
    }

    public CacheElement(Object data, String key, Cloneable.CloneType type) {
        this(data, key,0L, type);
    }

    public CacheElement(Object data, String key) {
        this(data, key, Cloneable.CloneType.NONE);
    }

    public void recordVisited() {
        visited.incrementAndGet();
    }

    public void recordTime(long time) {
        this.lastTime = time;
    }

    public int getVisited() {
        return visited.get();
    }

    public Object getValue() {
        return getValue(Cloneable.CloneType.NONE);
    }

    public Object getValue(Cloneable.CloneType cloneType) {
        if (cloneType == Cloneable.CloneType.CLONE)
            return cloneValue(this.value);
        return value;
    }

    private Object cloneValue(Object data) {
        if (Objects.isNull(data))
            return null;

        if (data instanceof List)
            return cloneList((List<?>) data);

        if (data instanceof Set)
            return cloneSet((Set<?>) data);

        if (data instanceof Map)
            return cloneMap((Map<?, ?>) data);

        if (data instanceof Cloneable)
            return ((Cloneable<?>) data).clone();

        ThrowProvider.doThrow(new CloneNotSupportedException(
                "Cannot clone type [" + data.getClass().getName() + "]. " +
                        "The object must implement [org.mind.framework.service.Cloneable]"));

        return null; // unreachable，让编译器满意
    }

    private <E> List<E> cloneList(List<?> source) {
        // 保留具体类型；无法反射构建时回退 ArrayList
        List<E> copy;
        try {
            copy = source.getClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            copy = new ArrayList<>(source.size());
        }

        for (Object item : source)
            copy.add((E) cloneItem(item));

        return copy;
    }

    private <E> Set<E> cloneSet(Set<?> source) {
        // 回退用 LinkedHashSet，保留插入顺序，比 HashSet 更安全
        Set<E> copy;
        try {
            copy = source.getClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            copy = new LinkedHashSet<>(source.size() << 1);
        }

        for (Object item : source)
            copy.add((E) cloneItem(item));

        return copy;
    }

    private <K, V> Map<K, V> cloneMap(Map<?, ?> source) {
        // 回退用 LinkedHashMap，保留插入顺序
        Map<K, V> copy;
        try {
            copy = source.getClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            copy = new LinkedHashMap<>(source.size() * 2);
        }

        for (Map.Entry<?, ?> entry : source.entrySet()) {
            K clonedKey   = (K) cloneItem(entry.getKey());
            V clonedValue = (V) cloneItem(entry.getValue());
            copy.put(clonedKey, clonedValue);
        }

        return copy;
    }

    /**
     * 克隆单个元素。
     * 对实现了 {@link Cloneable} 的对象执行深拷贝；
     * 对不可变类型（String、基础类型包装类、枚举）直接复用引用；
     * 其余类型抛出异常，要求调用方显式实现接口。
     */
    private Object cloneItem(Object item) {
        if (Objects.isNull(item))
            return null;

        if (item instanceof Cloneable)
            return ((Cloneable<?>) item).clone();

        // 不可变类型：String、基础类型包装类、枚举，复用引用是安全的
        if (item instanceof String
                || item instanceof Number
                || item instanceof Boolean
                || item instanceof Enum)
            return item;

        ThrowProvider.doThrow(new CloneNotSupportedException(
                "Collection element type [" + item.getClass().getName() + "] " +
                        "must implement [org.mind.framework.service.Cloneable]"));
        return null; // unreachable
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                .append("origKey", key)
                .append(" value", value)
                .append(" firstTime", firstTime)
                .append(" lastTime", lastTime)
                .append(" visited", getVisited())
                .append(" ttlMillis", ttlMillis)
                .toString();
    }
}
