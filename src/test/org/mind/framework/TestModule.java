package org.mind.framework;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.security.Base62Utils;
import org.mind.framework.service.threads.DynamicThreadPoolExecutor;
import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.util.FileUtils;
import org.mind.framework.util.JsonUtils;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.util.WeightedNode;
import org.mind.framework.util.WeightedRoundRobin;
import org.redisson.api.RLongAdder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @version 1.0
 * @author Marcus
 */
@Slf4j
public class TestModule {

    @Test
    public void base62() {
        // 测试数字编解码
        long number = 123456789L;
        String encoded = Base62Utils.encode(number);
        long decoded = Base62Utils.decodeToLong(encoded);
        System.out.println("数字: " + number + " -> Base62: " + encoded + " -> 解码: " + decoded);

        // 测试字符串编解码
        String text = "Hello, World! 你好世界！";
        String encodedStr = Base62Utils.encode(text);
        String decodedStr = Base62Utils.decodeToString(encodedStr);
        System.out.println("字符串: " + text + " -> Base62: " + encodedStr + " -> 解码: " + decodedStr);

        // 生成随机Base62字符串
        String randomStr = Base62Utils.randomString(10);
        System.out.println("随机Base62字符串: " + randomStr);

        // 验证Base62格式
        System.out.println("'" + encoded + "' 是否为有效Base62: " + Base62Utils.isValidBase62(encoded));
        System.out.println("'Hello@' 是否为有效Base62: " + Base62Utils.isValidBase62("Hello@"));
    }

    @Test
    public void testDynamicPool() throws InterruptedException {
        DynamicThreadPoolExecutor dynamicPool = new DynamicThreadPoolExecutor();

        System.out.println("Testing dynamic thread pool...");

        // 提交一些任务
        for (int i = 0; i < 2000; i++) {
            final int taskId = i;
            dynamicPool.execute(() -> {
                try {
                    System.out.println(Thread.currentThread().getName() + " - " + "Executing task " + taskId);
                    Thread.sleep(100); // 模拟任务执行
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            if (i % 45 == 0) {
                Thread.sleep(500); // 中间暂停，观察动态调整
            }
        }

        Thread.sleep(60000); // 等待任务完成和池调整
        dynamicPool.shutdown();
    }

    @SneakyThrows
    @Test
    public void test05() {
        // 创建待选项列表，每个节点对应一个服务或节点，以及指定权重值
        List<WeightedNode<String>> nodes = new ArrayList<>();
        nodes.add(WeightedNode.newNode("server1", 3));
        nodes.add(WeightedNode.newNode("server2", 2));
        nodes.add(WeightedNode.newNode("server3"));

        // 初始化加权轮询算法
        WeightedRoundRobin<String> wrr = new WeightedRoundRobin<>(nodes);

        // 依次获取下一个节点并输出
        for (int x = 0; x < 2; ++x) {
            ExecutorFactory.newThread("T-" + (x + 1), () -> {
                for (int i = 0; i < 12; ++i) {
                    WeightedNode<String> node = wrr.getNext();
                    System.out.println(Thread.currentThread().getName() + " : " + node.getValue());
                }
            }).start();
        }
        System.in.read();
    }

    @SneakyThrows
    @Test
    public void test04() {
        for (int i = 0; i < 1; ++i) {
            Thread t1 = ExecutorFactory.newThread(() -> {
                while (true) {
                    readBy();
                    try {
                        Thread.sleep(1000_000L);
                    } catch (InterruptedException e) {
                    }
                }
            });

            Thread t2 = ExecutorFactory.newThread(() -> {
                while (true) {
                    readBy();
                    try {
                        Thread.sleep(1000_000L);
                    } catch (InterruptedException e) {
                    }
                }
            });
            t1.start();
            t2.start();
        }

        System.in.read();
    }

    @SneakyThrows
    @Test
    public void readBy() {
        String rootDirectory = "/Users/marcus/Desktop";
        try (Stream<Path> streams = Files.list(Paths.get(rootDirectory))) {
            streams.filter(p -> StringUtils.endsWith(p.getFileName().toString(), ".json"))
                    .forEach(path -> {
                        String content = FileUtils.read(path, false, false);
                        System.out.println(path.getFileName().toString() + ": " + (content == null ? "null" : content.length()));
                        FileUtils.write(rootDirectory + "/1.js", content);
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }


        System.in.read();
    }


    @Test
    public void test03() {
        String source = "/home/${heelo}/xk/${count}";
        String param = "#{key}_#{value}#{key}";
        String r = MatcherUtils.convertURI(source);
        String r1 = MatcherUtils.convertParam(param);
        System.out.println(r);
        System.out.println(r1);
        System.out.println(MatcherUtils.checkCount(source, MatcherUtils.URI_PARAM_PATTERN));
        System.out.println(MatcherUtils.checkCount(param, MatcherUtils.PARAM_MATCH_PATTERN));
        String key = "83m";
        System.out.println(MatcherUtils.matcher("/home/#/xk/3", r));
        System.out.println(Pattern.compile("#\\{key\\}").matcher(param).replaceAll(key));
    }

    @Test
    public void test02() {
        String json = "\n" +
                "  {  \n" +
                "\n" +
                "\"jsonrpc\":\"2.0\",\n" +
                "\n" +
                "\"method\":\"eth_getTransactionCount\",\n" +
                "\n" +
                "\"params\":[\n" +
                "\n" +
                "\"0xea674fdde714fd979de3edf0f56aa9716b898ec8\",\n" +
                "\n" +
                "\"0x658a13\"\n" +
                "\n" +
                "],\n" +
                "\n" +
                "\"id\":1\n" +
                "\n" +
                "}  \n  ";

        System.out.println(JsonUtils.deletionBlank(json));
        json = StringUtils.substringBetween(json, "method", ",");
        json = json.replaceAll("['\":]*", "").trim();
        System.out.println(json);
    }

    @SneakyThrows
    @Test
    public void test01() {
        ExecutorFactory.newThread(() -> {
            while (true) {
                long sum = read();
                System.out.println(keyName + "\t" + sum);
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                }
            }
        }).start();

        ExecutorFactory.newThread(() -> {
            while (true) {
                written();
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                }
            }
        }).start();

        addEvent();

//        RedissonHelper helper = RedissonHelper.getInstance();
//        for (int i = 0; i < 10; ++i) {
//            System.out.println(helper.getIdForDate());
//            System.out.println(helper.getId(0L, 1000L));
//            System.out.println("--------------");
//        }
        System.in.read();
    }

    String keyName = "orgid:20230725:751";

    private long read() {
        RLongAdder adder = RedissonHelper.getClient().getLongAdder(keyName);
        adder.increment();
        return adder.sum();
    }

    private void written() {
        RedissonHelper.getClient().getLongAdder(keyName).increment();
    }

    private void addEvent() {
        RedissonHelper.getInstance().addShutdownEvent(redissonClient -> {
            long sum = read();
            redissonClient.getAtomicLong("shutdown:adder").set(sum);
            System.out.println("shutdown: " + sum);
        });
    }

    @Test
    @SneakyThrows
    public void test041() {
        RedissonHelper helper = RedissonHelper.getInstance();
        String name = "lz:test:map";
        Map<String, Object> map = new HashMap<>();
        map.put("s1", 24);
        map.put("s2", "25");
        map.put("m1", "32");
        map.put("e1", true);

        helper.setAsync(name, map, 10, TimeUnit.MINUTES).whenComplete((w, e) -> {
            System.out.println(w);
            Map<String, Object> newmap = helper.getMap(name);
            System.out.println(newmap.size());
            newmap.forEach((k, v) -> System.out.println(k + "\t" + v));
        });

//        Map<String, Object> newmap = helper.getMap(name);
//        System.out.println(newmap.size());
//        newmap.forEach((k, v) -> System.out.println(k + "\t" + v));
        System.in.read();
    }

}
