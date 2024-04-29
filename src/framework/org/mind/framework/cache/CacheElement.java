package org.mind.framework.cache;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.mind.framework.exception.ThrowProvider;
import org.mind.framework.service.Cloneable;
import org.mind.framework.util.DateUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CacheElement {

    // cached object
    private Object value;

    // The time recorded during the cache, if accessed, the last time will be recorded
    private long lastTime;

    private long firstTime;

    // hit rate
    private int visited;

    private String key;

    public CacheElement() {

    }

    public CacheElement(Object data, String key, long time, int visited, Cloneable.CloneType type) {
        this.firstTime = time;
        this.lastTime = time;
        this.visited = visited;
        this.key = key;

        switch (type) {
            case ORIGINAL:
                this.value = data;
                break;
            case CLONE:
                this.value = this.cloneValue(data);
                break;
        }
    }

    public CacheElement(Object data, String key, Cloneable.CloneType type) {
        this(data, key, DateUtils.getMillis(), 0, type);
    }

    public CacheElement(Object data, String key) {
        this(data, key, Cloneable.CloneType.ORIGINAL);
    }

    public void recordVisited() {
        visited++;
    }

    public int getVisited() {
        return visited;
    }

    public String getKey() {
        return key;
    }

    public Object getValue() {
        return getValue(Cloneable.CloneType.ORIGINAL);
    }

    public Object getValue(Cloneable.CloneType cloneType) {
        if (cloneType == Cloneable.CloneType.CLONE)
            return cloneValue(this.value);
        return value;
    }

    private Object cloneValue(Object data) {
        if (data instanceof List) {
            List<?> list = (List<?>) data;
            if (list.isEmpty())
                return data;

            List<?> copierList = new ArrayList<>(list.size());
            addCollection(copierList, list);
            return copierList;
        } else if (data instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) data;
            if (map.isEmpty())
                return data;

            Map<?, ?> copierMap = new HashMap<>(map.size());
            this.push(copierMap, map);
            return copierMap;
        } else if (data instanceof Set) {
            Set<?> set = (Set<?>) data;
            if (set.isEmpty())
                return data;

            Set<?> copierSet = new HashSet<>(set.size());
            addCollection(copierSet, set);
            return copierSet;
        } else if (data instanceof Cloneable) {
            return ((Cloneable<?>) data).clone();
        } else
            ThrowProvider.doThrow(new CloneNotSupportedException("The clone object needs to implement [org.mind.framework.service.Cloneable]"));

        return data;
    }

    private void addCollection(Collection copier, Collection<?> source) {
        source.forEach(item -> {
            if (item instanceof Cloneable) {
                copier.add(((Cloneable<?>) item).clone());
            } else
                ThrowProvider.doThrow(new CloneNotSupportedException("The clone object needs to implement [org.mind.framework.service.Cloneable]"));
        });
    }

    private void push(Map copier, Map<?, ?> source) {
        source.forEach((k, v) -> {
            if (v instanceof Cloneable) {
                copier.put(k, ((Cloneable<?>) v).clone());
            } else
                ThrowProvider.doThrow(new CloneNotSupportedException("The clone object needs to implement [org.mind.framework.service.Cloneable]"));
        });
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public long getLastTime() {
        return lastTime;
    }

    public void recordTime(long time) {
        this.lastTime = time;
    }

    public long getFirstTime() {
        return firstTime;
    }

    public void setFirstTime(long firstTime) {
        this.firstTime = firstTime;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("key", key)
                .append(" value", value)
                .append(" firstTime", firstTime)
                .append(" lastTime", lastTime)
                .append(" visited", visited)
                .toString();
    }
}
