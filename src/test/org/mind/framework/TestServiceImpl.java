package org.mind.framework;

import lombok.extern.slf4j.Slf4j;
import org.mind.framework.annotation.Cachein;
import org.mind.framework.annotation.EnableCache;
import org.mind.framework.service.Cloneable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2022/9/5
 */
@Slf4j
@EnableCache
@Service("testService")
public class TestServiceImpl implements TestService {

    private static final String CACHE_KEY = "user_by_id";

    @Override
    @Cachein(cacheable = "cacheManager", prefix = CACHE_KEY, suffix = "#{userId}_#{vars}", strategy = Cloneable.CloneType.ORIGINAL)
    public List<Object> get(String vars, long userId){
        log.debug("--------------执行方法: get");
        return Arrays.asList(234L, 32, 32);
    }


    @Override
    @Cachein(cacheable = "cacheManager", suffix = "#{userId}")
    public String byCache(long userId){
        log.debug("--------------执行方法: byCache");
        return "bycache_"+ userId;
    }

}
