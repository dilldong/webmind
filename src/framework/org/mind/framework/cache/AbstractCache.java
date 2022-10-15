package org.mind.framework.cache;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractCache {

    private static final Logger log = LoggerFactory.getLogger(AbstractCache.class);

    private String cacheName = "MY-Cache";

    protected AbstractCache() {
    }

    protected AbstractCache(String cacheName) {
        this.cacheName = cacheName;
    }

    protected String realKey(Object key) {
        return realKey(null, key);
    }

    protected String realKey(String prefix, Object key) {
        if (StringUtils.isEmpty(prefix))
            return key.toString();

        return String.join("_", prefix, key.toString());
    }

    /**
     * Destroy cache
     *
     * @author dp
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
