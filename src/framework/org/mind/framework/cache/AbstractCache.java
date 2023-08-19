package org.mind.framework.cache;

import org.apache.commons.lang3.StringUtils;
import org.mind.framework.web.Destroyable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Destroy cache
 *
 * @author dp
 * @date Nov 26, 2010
 */
public abstract class AbstractCache implements Destroyable {

    private static final Logger log = LoggerFactory.getLogger(AbstractCache.class);

    public static final String CACHE_DELIMITER = "_";

    private String cacheName = "Webmind-Cache";

    protected AbstractCache() {
    }

    protected AbstractCache(String cacheName) {
        this.cacheName = cacheName;
    }

    protected String realKey(String key) {
        return realKey(null, key);
    }

    protected String realKey(String prefix, String key) {
        if (StringUtils.isEmpty(prefix))
            return key;

        return String.join(CACHE_DELIMITER, prefix, key);
    }

    @Override
    public void destroy() {
        if(log.isDebugEnabled())
            log.debug("Destroy {} manager.", cacheName);
    }

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }
}
