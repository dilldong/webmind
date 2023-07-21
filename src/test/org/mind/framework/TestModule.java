package org.mind.framework;

import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.service.threads.ExecutorFactory;
import org.mind.framework.util.FileUtils;
import org.mind.framework.util.JsonUtils;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.util.WeightedNode;
import org.mind.framework.util.WeightedRoundRobin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @version 1.0
 * @auther Marcus
 */
public class TestModule {

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

    private void readBy() {
        String rootDirectory = "/Users/marcus/Desktop/eventdata";
        try (Stream<Path> streams = Files.list(Paths.get(rootDirectory))) {
            streams.filter(p -> StringUtils.endsWith(p.getFileName().toString(), ".json"))
                    .forEach(path -> {
                        String content = FileUtils.read(path, false, false);
                        System.out.println(path.getFileName().toString() + ": " + (content == null ? "null" : content.length()));
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void test03() {
        String source = "/home/${heelo}/xk/${count}";
        String param = "#{key}_#{value}#{key}";
        String r = MatcherUtils.convertURI(source);
        String r1 = MatcherUtils.convertParam(param);
        System.out.println(r);
        System.out.println(r1);
        System.out.println(MatcherUtils.checkCount(param, MatcherUtils.PARAM_MATCH_PATTERN));
        String key = "83m";
        System.out.println(Pattern.compile("#\\{key\\}").matcher(param).replaceAll(key));
        ;

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
        json = json.replaceAll("[\'\":]*", "").trim();
        System.out.println(json);
    }

    @Test
    public void test01() {
        RedissonHelper helper = RedissonHelper.getInstance();

        for (int i = 0; i < 10; ++i) {
            System.out.println(helper.getIdForDate());
            System.out.println(helper.getId(0L, 1000L));
            System.out.println("--------------");
        }
    }
}

