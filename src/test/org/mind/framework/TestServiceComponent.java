package org.mind.framework;

import lombok.extern.slf4j.Slf4j;
import org.mind.framework.annotation.Cachein;
import org.mind.framework.annotation.EnableCache;
import org.mind.framework.service.Cloneable;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2022/9/5
 */
@Slf4j
@Service
@EnableCache(exposeProxy = true)
public class TestServiceComponent {

    private static final String CACHE_KEY = "user_by_id";

    @Cachein(prefix = CACHE_KEY, suffix = "#{userId}_#{vars}", strategy = Cloneable.CloneType.ORIGINAL)
    public List<Object> get(String vars, long userId) {
        log.debug("--------------未实现interface: get");
        return Arrays.asList(234L, 32, 32);
    }


    @Cachein(suffix = "#{userId}")
    public String byCache(long userId) {
        TestServiceComponent t = ((TestServiceComponent) AopContext.currentProxy());
        t.get("first", 832834L);
        t.get("hello", userId);

        log.debug("--------------未实现interface: byCache");
        return "bycache_" + userId;
    }

}
