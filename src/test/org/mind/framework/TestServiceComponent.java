package org.mind.framework;

import lombok.extern.slf4j.Slf4j;
import org.mind.framework.annotation.Cachein;
import org.mind.framework.annotation.EnableCache;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Marcus
 * @version 1.0
 * @date 2022/9/5
 */
@Slf4j
@Service
@EnableCache(exposeProxy = true)
public class TestServiceComponent {

    private static final String CACHE_KEY = "user_by_id";

    @Cachein(prefix = CACHE_KEY,
            suffix = "#{userId}_#{vars}",
            expire = 1L,
            unit = TimeUnit.HOURS)
    public List<Object> getWithCache(String vars, long userId) {
        log.debug("-------1.call orig method-------");
        return Arrays.asList(234L, 32, 32);
    }

    @Cachein(prefix = CACHE_KEY,
            expire = 10L,
            unit = TimeUnit.SECONDS,
            cacheNull = true)
    public List<String> getNullWithCache() {
        log.debug("-------1.cache null-------");
        return null;
    }

    @Cachein(prefix = CACHE_KEY,
            suffix = "#{userId}",
            expire = 1L,
            unit = TimeUnit.HOURS)
    public String getWithCache(long userId) {
        log.debug("-------2.call orig method-------");
        return "bycache_" + userId;
    }

}
