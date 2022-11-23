package org.mind.framework;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
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
import org.mind.framework.util.DateFormatUtils;
import org.mind.framework.util.IOUtils;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.util.RandomCodeUtil;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.Resource;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * @version 1.0
 * @auther Marcus
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:spring/springContext.xml", "classpath:spring/businessConfig.xml"})
public class TestModel extends AbstractJUnit4SpringContextTests {

    @Resource
    private TestService testService;

    @Resource
    private TestServiceComponent testServiceComponent;

    @Resource
    private QueueService executorQueueService;

    @Test
    public void test10(){
        parseDateValue("Sun, 06 Nov 1994 08:49:37 GMT");
        System.out.println("------------------");
        parseDateValue("Sunday, 06-Nov-94 08:49:37 GMT");
        System.out.println("------------------");
        parseDateValue("Sun Nov 6 08:49:37 1994");
    }


    private long parseDateValue(String headerValue) {
        if (StringUtils.isEmpty(headerValue))
            return -1;// No header value sent at all

        String[] DATE_FORMATS = new String[]{
                "EEE, dd MMM yyyy HH:mm:ss z",
                "EEEEEE, dd-MMM-yy HH:mm:ss zzz",
                "EEE MMMM d HH:mm:ss yyyy"
        };

        if (headerValue.length() >= 3) {
            for (String dateFormat : DATE_FORMATS) {
                System.out.println(dateFormat);
                try {
                    LocalDateTime localDateTime =
                            LocalDateTime.parse(headerValue, DateTimeFormatter.ofPattern(dateFormat, Locale.US));
                    long mills = localDateTime.atZone(DateFormatUtils.UTC).toEpochSecond() * 1000L;
                    System.out.println("1: "+ mills);
                    break;
                } catch (DateTimeParseException | IllegalArgumentException e){
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat, Locale.US);
                    simpleDateFormat.setTimeZone(DateFormatUtils.UTC_TIMEZONE);
                    try {
                        long mills = simpleDateFormat.parse(headerValue).getTime();
                        System.out.println("2: "+ mills);
                        break;
                    } catch (ParseException ex) {
                        // ignore exception
                    }
                }
            }
        }
        return -1;
    }

    @SneakyThrows
    @Test
    public void test09() {
        this.applicationContext.getBean("mainService", WebMainService.class).start();

        int i = 2000;
        while ((--i) >= 0) {
            A a = A.builder().field01("" + (i + 1)).build();
            executorQueueService.producer(() -> {
                System.out.println(a.field01);
            });
        }

        System.in.read();
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