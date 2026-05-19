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
import org.mind.framework.config.AppConfiguration;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.security.RSA2Utils;
import org.mind.framework.service.Cloneable;
import org.mind.framework.service.queue.QueueService;
import org.mind.framework.util.CalculateUtils;
import org.mind.framework.util.DateUtils;
import org.mind.framework.util.IOUtils;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.util.RandomCodeUtil;
import org.redisson.api.RMapCache;
import org.redisson.api.RPatternTopic;
import org.redisson.api.map.event.EntryCreatedListener;
import org.redisson.api.map.event.EntryExpiredListener;
import org.redisson.api.map.event.EntryRemovedListener;
import org.redisson.api.map.event.EntryUpdatedListener;
import org.redisson.client.codec.StringCodec;
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
import java.util.concurrent.TimeUnit;

/**
 * @version 1.0
 * @author Marcus
 */
@RunWith(SpringJUnit4ClassRunner.class)
//@ContextConfiguration(locations = {"classpath:spring/springContext.xml"})
@ContextConfiguration(classes = AppConfiguration.class)
public class TestSpringModule extends AbstractJUnit4SpringContextTests {

    @Resource
    private TestServiceComponent testServiceComponent;

    @Resource
    private QueueService queueService;

    @Resource
    private Cacheable cacheable;

    @SneakyThrows
    @Test
    public void nestedQueue() {
        queueService.producer(()->{
            System.out.println("task-1");

            Thread.currentThread().interrupt();

            queueService.producer(()->{
                System.out.println("task-1-1");

                queueService.producer(()->{
                    System.out.println("task-1-1-1");

                    queueService.producer(()->{
                        System.out.println("task-1-1-1-1");
                    });
                });
            });
        });

        queueService.producer(()->{
            System.out.println("task-2");

            queueService.producer(()->{
                System.out.println("task-2-1");
            });
        });

        System.in.read();
    }

    @Test
    public void test10() {
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
//                "EEE, dd MMM yyyy HH:mm:ss z",
//                "EEEEEE, dd-MMM-yy HH:mm:ss zzz",
//                "EEE MMMM d HH:mm:ss yyyy"
                "EEE, dd MMM yyyy HH:mm:ss zzz",
                "EEE, dd-MMM-yy HH:mm:ss zzz",
                "EEE MMM dd HH:mm:ss yyyy"
        };

        if (headerValue.length() >= 3) {
            for (String dateFormat : DATE_FORMATS) {
                System.out.println(dateFormat);
                try {
                    LocalDateTime localDateTime =
                            LocalDateTime.parse(headerValue, DateTimeFormatter.ofPattern(dateFormat, Locale.US));
                    long mills = localDateTime.atZone(DateUtils.UTC).toEpochSecond() * 1000L;
                    System.out.println("1: " + mills);
                    break;
                } catch (DateTimeParseException | IllegalArgumentException e) {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat, Locale.US);
                    simpleDateFormat.setTimeZone(DateUtils.UTC_TIMEZONE);
                    try {
                        long mills = simpleDateFormat.parse(headerValue).getTime();
                        System.out.println("2: " + mills);
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
        int i = 10;
        while ((--i) >= 0) {
            A a = A.builder().field01("" + (i + 1)).build();
            queueService.producer(() -> {
                try {
                    System.out.println(Thread.currentThread().getName() + "\tblocking");
                    Thread.sleep(3_000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                System.out.println(Thread.currentThread().getName() + "\t" + a.field01);

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
        int count = MatcherUtils.checkCount(v, MatcherUtils.URI_PARAM_PATTERN);
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

    @SneakyThrows
    @Test
    public void testLocalCache(){
        System.out.println("1.call string:");
        System.out.println(testServiceComponent.getWithCache(22222L));
        Thread.sleep(1000L);

        System.out.println("2.call string:");
        System.out.println(testServiceComponent.getWithCache(22222L));
    }

    @SneakyThrows
    @Test
    public void keySpaceEvent(){
        // 订阅删除事件
        RPatternTopic delTopic = RedissonHelper.getClient().getPatternTopic("__keyevent@0__:del", StringCodec.INSTANCE);
        delTopic.addListener(String.class, ( pattern, channel, message) -> {
            System.out.println("[KeyEvent] 被删除: " + message);
        });

        // 订阅过期事件
        RPatternTopic expiredTopic = RedissonHelper.getClient().getPatternTopic("__keyevent@0__:expired");
        expiredTopic.addListener(String.class, (pattern, channel, message) -> {
            System.out.println("key 已过期: " + message);
        });

        // 订阅淘汰事件
        RPatternTopic evictedTopic = RedissonHelper.getClient().getPatternTopic("__keyevent@0__:evicted");
        evictedTopic.addListener(String.class, (pattern, channel, message) -> {
            System.out.println("key 被淘汰: " + message);
        });

        // 订阅创建事件
        RPatternTopic newTopic = RedissonHelper.getClient().getPatternTopic("__keyspace@0__:newKey");
        newTopic.addListener(String.class, (pattern, channel, message) -> {
            System.out.println("key 被创建: " + message);
        });

        // 订阅创建事件
        RPatternTopic overrideTopic = RedissonHelper.getClient().getPatternTopic("__keyspace@0__:counter");
        overrideTopic.addListener(String.class, (pattern, channel, message) -> {
            System.out.println("key 被覆盖: " + message);
        });

        System.out.println("Redis KeySpace事件监听器已启动");
        System.in.read();
    }

    @SneakyThrows
    @Test
    public void keyListenAll(){
        // 订阅所有 key 的事件（注意频道格式）
        // __keyevent@0__:* 中的 0 代表 db0，根据你的 Redis 数据库序号调整
        RPatternTopic topic = RedissonHelper.getClient().getPatternTopic("__keyevent@0__:*");

        topic.addListener(String.class,  (pattern, channel, message) -> {
            String eventChannel = channel.toString();
            String key = message;

            System.out.println("收到事件 - 频道: " + eventChannel + ", Key: " + key);

            // 判断事件类型
            if (eventChannel.endsWith(":del")) {
                System.out.println("Key 被删除: " + key);
                // 在这里执行你的业务逻辑
            } else if (eventChannel.endsWith(":expired")) {
                System.out.println("Key 已过期: " + key);
            } else if (eventChannel.endsWith(":evicted")) {
                System.out.println("Key 被内存淘汰: " + key);
            }
        });

        System.out.println("Redis 全局事件监听器已启动");
        System.in.read();
    }

    @SneakyThrows
    @Test
    public void cacheListen(){
        RMapCache<String, Object> cache = RedissonHelper.getClient().getMapCache("webmind:cachemap:listen");
        registryEvent(cache);

        System.out.println("new ...");
        cache.fastPut("user_by_1", StringUtils.EMPTY, 60L, TimeUnit.SECONDS);
        cache.fastPut("user_by_2", StringUtils.EMPTY, 10L, TimeUnit.SECONDS);

        TimeUnit.SECONDS.sleep(3L);
        System.out.println("deleted ...");
        cache.fastRemove("user_by_2");

        System.out.println("update ...");
        cache.fastPut("user_by_2", "update", 5L, TimeUnit.SECONDS);

        System.in.read();
    }

    private void registryEvent(RMapCache<String, Object> cache){
        cache.addListener((EntryRemovedListener<String, Object>) event -> {
            System.out.println("Entry removed, key=" + event.getKey());
        });
        cache.addListener((EntryExpiredListener<String, Object>) event -> {
            System.out.println("Entry expired, key=" + event.getKey());
        });
        cache.addListener((EntryUpdatedListener<String, Object>) event -> {
            System.out.println("Entry updated, key=" + event.getKey() + ", newVal=" + event.getValue());
        });
        cache.addListener((EntryCreatedListener<String, Object>) event -> {
            System.out.println("Entry created, key=" + event.getKey() + ", newVal=" + event.getValue());
        });
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
        System.out.println(RandomCodeUtil.randomString(10, false, true));
    }


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class A implements Cloneable<A> {
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
        int num01;
        long num02;

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
}

