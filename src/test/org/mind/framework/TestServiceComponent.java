package org.mind.framework;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mind.framework.annotation.Cachein;
import org.mind.framework.cache.AbstractCache;
import org.mind.framework.helper.broadcast.RedissonStreamBroadcastService;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Marcus
 * @version 1.0
 * @date 2022/9/5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestServiceComponent {
    private final RedissonStreamBroadcastService broadcastService;

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

    public void clear(long userId){
        log.info("发布消息");
        broadcastService.publish(String.join(AbstractCache.CACHE_DELIMITER, CACHE_KEY, String.valueOf(userId)));
    }
}
