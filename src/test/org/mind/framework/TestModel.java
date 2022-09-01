package org.mind.framework;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.mind.framework.util.RandomCodeUtil;

/**
 * @version 1.0
 * @auther Marcus
 */
@Slf4j
//@RunWith(SpringJUnit4ClassRunner.class)
//@ContextConfiguration(locations = {"classpath:spring/springContext.xml", "classpath:spring/businessConfig.xml", "classpath:spring/mybatis-plus.xml"})
public class TestModel {

    @Test
    public void test01(){
        System.out.println(RandomCodeUtil.getRandomString(10, false, true));
    }
}
