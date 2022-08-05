package org.mind.framework.cache;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractCache {

    private static final Logger log = LoggerFactory.getLogger(AbstractCache.class);

//	public abstract int getSize();

//	public abstract String[] names();

    private String cacheName = "MY-Cache";

    protected AbstractCache() {

    }

    protected AbstractCache(String cacheName) {
        this.cacheName = cacheName;
    }

    protected void init() {

    }

    protected void process() {

    }

    protected String realKey(Object key) {
        return realKey(null, key);
    }

    protected String realKey(String prefix, Object key) {
        if (StringUtils.isBlank(prefix))
            return key.toString();

        return new StringBuffer()
                .append(prefix)
                .append(".")
                .append(key)
                .toString();
    }

    /**
     * 关闭Cache
     *
     * @author dongping
     * @date Nov 26, 2010
     */
    protected void destroy() {
        log.info("Destroy {} manager.", cacheName);
    }

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }
}
