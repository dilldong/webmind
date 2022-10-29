package org.mind.framework;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mind.framework.cache.CacheElement;
import org.mind.framework.cache.Cacheable;
import org.mind.framework.cache.LruCache;
import org.mind.framework.security.RSA2Utils;
import org.mind.framework.service.Cloneable;
import org.mind.framework.service.WebMainService;
import org.mind.framework.service.queue.QueueService;
import org.mind.framework.util.CalculateUtils;
import org.mind.framework.util.IOUtils;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.util.RandomCodeUtil;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
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
public class TestModel extends AbstractJUnit4SpringContextTests {

    @Resource
    private TestService testService;

    @Resource
    private TestServiceComponent testServiceComponent;

    @Resource
    private QueueService executorQueueService;

    @SneakyThrows
    @Test
    public void test09() {
        WebMainService service = this.applicationContext.getBean("mainService", WebMainService.class);
        int i = 2;
        while ((--i) >= 0) {
            A a = A.builder().field01("" + i).build();
            executorQueueService.producer(() -> {
                System.out.println(a.field01);
            });
        }

        service.start();
        Thread.sleep(2000L);
        service.stop();
    }

    @Test
    public void test08() {
        RSA2Utils.RSA2 rsa = RSA2Utils.generateKey();
        String orig = "dsakldfhkj*^=~圣诞节";
        String encdata = RSA2Utils.encrypt(orig, rsa.getPublicByBase64());
        String decdata = RSA2Utils.decrypt(encdata, rsa.getPrivateByBase64());
        System.out.println(rsa);
        System.out.println(encdata);
        System.out.println(decdata);

        String signStr = RSA2Utils.sign(encdata, rsa.getPrivateByBase64());
        System.out.println("签名: " + signStr);

        System.out.println("签名验证: " + RSA2Utils.verify(encdata, rsa.getPublicByBase64(), signStr));
    }

    @Test
    public void test07() {
        String v = "/user/${id}";

        String regex = MatcherUtils.convertURI(v);
        System.out.println(regex);
        int count = MatcherUtils.checkCount(v, MatcherUtils.URI_PARAM_MATCH);
        System.out.println("count:" + count);

        String uri = "/user/1332?id=13&path=http://www.c.cs/user/idja/tt";
        boolean f = MatcherUtils.matcher(uri, regex);
        System.out.println(f);


        String res = "css|js|jpg|png|gif|html|htm|xls|xlsx|doc|docx|ppt|pptx|pdf|rar|zip|txt";

        f = MatcherUtils.matcher("TXT", res, MatcherUtils.IGNORECASE_EQ).matches();
        System.out.println("=====" + f);
    }

    @Test
    public void test06() {
        int i = 132;
        byte b = IOUtils.int2byte(i)[3];
        int k = IOUtils.bytesToInt(new byte[]{b}, 0, 1);
        System.out.println(b);
        System.out.println(k);
    }

    @Test
    public void test05() {
        System.out.println(CalculateUtils.formatNumberSymbol("1002"));
    }

    @Test
    public void test04() {
        List<Object> list = testService.get("first", 832834L);
        list.forEach(obj -> System.out.println(obj));

        testService.get("second", 23784234).forEach(obj -> System.out.println(obj));
        testService.get("first", 832834L).forEach(obj -> System.out.println(obj));

        System.out.println(testService.byCache(32342));
        System.out.println(testService.byCache(32342));
        System.out.println(testService.getClass().getName());

        System.out.println("testServiceComponent: ");
        System.out.println(testServiceComponent.byCache(22222));
        System.out.println(testServiceComponent.getClass().getName());
    }

    @Test
    public void test03() {
        Cacheable cacheable = LruCache.initCache();
        String key = "1232131";

        List<A> list = new ArrayList<>(3);
        cacheable.addCache(key, list, true, Cloneable.CloneType.CLONE);

        for (int i = 0; i < 3; ++i) {
            A a = A.builder().build();
            list.add(a);
        }

        System.out.println("new: " + Arrays.toString(list.toArray()));

        CacheElement element = cacheable.getCache(key);
        List<A> list1 = (List<A>) element.getValue(Cloneable.CloneType.CLONE);
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
        A a1 = (A) element.getValue(Cloneable.CloneType.CLONE);
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