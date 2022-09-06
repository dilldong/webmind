package org.mind.framework;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mind.framework.cache.CacheElement;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.cache.LruCache;
import org.mind.framework.service.Cloneable;
import org.mind.framework.util.RandomCodeUtil;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @version 1.0
 * @auther Marcus
 */
@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:spring/springContext.xml", "classpath:spring/businessConfig.xml"})
public class TestModel {

    @Resource
    private TestService testService;

    @Test
    public void test04() {
        List<Object> list = testService.get("first", 832834L);
        list.forEach(obj -> System.out.println(obj));

        testService.get("second", 23784234).forEach(obj -> System.out.println(obj));
        testService.get("first", 832834L).forEach(obj -> System.out.println(obj));

        System.out.println(testService.byCache(32342));
        System.out.println(testService.byCache(32342));
        System.out.println(testService.getClass().getName());
    }

    @Test
    public void test03() {
        Cacheable cacheable = LruCache.initCache();
        String key = "1232131";

        List<A> list = new ArrayList<>(3);
        cacheable.addCache(key, list, true, Cloneable.CloneType.CLONE);

        for (int i = 0; i < 3; i++) {
            A a = A.builder().build();
            list.add(a);
        }

        System.out.println("new: " + Arrays.toString(list.toArray()));

        CacheElement element = cacheable.getCache(key);
        List<A> list1 = (List<A>) element.getValue(true);
        list1.get(0).setField01("这是一个clone后的setting");
        System.out.println("clone Set: " + Arrays.toString(list1.toArray()));

        System.out.println("clone Get: " + Arrays.toString(((List<A>) element.getValue()).toArray()));

        List<A> list2 = (List<A>) element.getValue();
        list2.get(1).setField01("not clone后的setting");

        System.out.println("再次Get: " + Arrays.toString(((List<A>) element.getValue()).toArray()));
    }

    @Test
    public void test02() {
        Cacheable cacheable = LruCache.initCache();
        String key = "1232131";
        A a = A.builder().build();
        cacheable.addCache(key, a, true);
        System.out.println("new: " + a);

        CacheElement element = cacheable.getCache(key);
        A a1 = (A) element.getValue(true);
        a1.setField01("这是一个clone后的setting");
        System.out.println("clone Set: " + a1);

        System.out.println("clone Get: " + element.getValue());

        A a2 = (A) element.getValue();
        a2.setField01("not clone后的setting");

        System.out.println("再次Get: " + element.getValue());
    }

    @Test
    public void test01() {
        System.out.println(RandomCodeUtil.getRandomString(10, false, true));
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class A implements Cloneable<A> {
    String field01;
    String field02;
    String field03;
    String field04;
    String field05;
    String field06;
    String field07;
    String field08;
    String field09;
    String field10;


    @Override
    public A clone() {
        try {
            System.out.println("call clone....");
            return (A) super.clone();
        } catch (CloneNotSupportedException e) {
        }
        return this;
    }
}