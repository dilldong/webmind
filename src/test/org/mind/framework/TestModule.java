package org.mind.framework;

import org.junit.Test;
import org.mind.framework.helper.RedissonHelper;

/**
 * @version 1.0
 * @auther Marcus
 */
public class TestModule {

    @Test
    public void test01(){
        RedissonHelper helper = RedissonHelper.getInstance();

        for(int i=0; i<10; ++i) {
            System.out.println(helper.getIdForDate());
            System.out.println(helper.getId(0L, 1000L));
            System.out.println("--------------");
        }
    }
}

