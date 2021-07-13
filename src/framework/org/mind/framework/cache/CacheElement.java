package org.mind.framework.cache;

import org.mind.framework.util.DateFormatUtils;

public class CacheElement {

    // 缓存的对象
    private Object value;

    // 缓存时记录的时间，如有访问过，就记录最近一次访问时间
    private long time;

    private long firstTime;

    // 命中率次数
    private int visited;

    public CacheElement() {

    }

    public CacheElement(Object data, long time, int visited) {
        this.value = data;
        this.firstTime = time;
        this.time = time;
        this.visited = visited;
    }

    public CacheElement(Object data) {
        this(data, DateFormatUtils.getTimeMillis(), 0);
    }

    public void recordVisited() {
        visited++;
    }

    public int getVisited() {
        return visited;
    }

    public void setVisited(int visited) {
        this.visited = visited;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public long getLastedTime() {
        return time;
    }

    public void recordTime(long time) {
        this.time = time;
    }

    public long getFirstTime() {
        return firstTime;
    }

    public void setFirstTime(long firstTime) {
        this.firstTime = firstTime;
    }

    @Override
    public String toString() {
        return new StringBuilder("CacheElement{")
                .append("value=").append(value)
                .append(", time=").append(time)
                .append(", firstTime=").append(firstTime)
                .append(", visited=").append(visited)
                .append('}')
                .toString();
    }
}
