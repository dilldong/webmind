package org.mind.framework;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mind.framework.annotation.Cachein;
import org.mind.framework.cache.AbstractCache;
import org.mind.framework.helper.broadcast.RedissonStreamBroadcastService;
import org.mind.framework.helper.delayqueue.DelayTask;
import org.mind.framework.helper.delayqueue.RedissonStreamDelayQueueService;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
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
    private final RedissonStreamBroadcastService cacheBroadcastService;

    private final RedissonStreamDelayQueueService redissonStreamDelayQueueService;
    private final RedissonStreamDelayQueueService redissonStreamDelayQueueService02;

    private static final String CACHE_KEY = "user_by_id";

    @PostConstruct
    private void init(){
        redissonStreamDelayQueueService02.registerConsumer(DelayTask.class, task -> {
            System.out.println("2: RDelay开始消费: ");
            System.out.println(task);
        });
    }

    public void sendMessage(){
        redissonStreamDelayQueueService.addDelayTask(DelayTask.of("webmind发送的测试消息"), 1L, TimeUnit.SECONDS);
    }

    @Cachein(prefix = CACHE_KEY,
            suffix = "#{userId}_#{vars}",
            expire = 15L,
            unit = TimeUnit.SECONDS,
            cacheNull = true)
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
            expire = 15L,
            unit = TimeUnit.SECONDS)
    public String getWithCache(long userId) {
        log.debug("-------call orig method-------");
        return "bycache_" + userId;
    }

    public void clear(long userId){
        log.info("发布消息");
        cacheBroadcastService.publish(String.join(AbstractCache.CACHE_DELIMITER, CACHE_KEY, String.valueOf(userId)));
    }
}
