package org.mind.framework;

import lombok.extern.slf4j.Slf4j;
import org.mind.framework.annotation.Cachein;
import org.mind.framework.annotation.EnableCache;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @version 1.0
 * @author Marcus
 * @date 2022/9/5
 */
@Slf4j
@EnableCache
@Service("testService")
public class TestServiceImpl implements TestService {

    private static final String CACHE_KEY = "user_by_id";

    @Override
    @Cachein(prefix = CACHE_KEY,
            suffix = "#{userId}_#{vars}",
            expire = 1L,
            unit = TimeUnit.HOURS,
            penetration = true,
            inRedis = true,
            redisType = List.class)
    public List<Object> get(String vars, long userId){
        log.debug("--------------执行方法: get");
//        return Collections.emptyList();
        return List.of(234L, 32, 32);
    }


    @Override
    @Cachein(prefix = CACHE_KEY,
            suffix = "#{userId}",
            expire = 1L,
            unit = TimeUnit.HOURS,
            inRedis = true,
            redisType = String.class)
    public String byCache(long userId){
        log.debug("--------------执行方法: byCache");
        return null;//"bycache_"+ userId;
    }

}
