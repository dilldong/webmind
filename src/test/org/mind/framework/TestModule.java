package org.mind.framework;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.mind.framework.helper.RedissonHelper;
import org.mind.framework.util.JsonUtils;
import org.mind.framework.util.MatcherUtils;

import java.util.regex.Pattern;

/**
 * @version 1.0
 * @auther Marcus
 */
public class TestModule {

    @Test
    public void test03(){
        String source = "/home/${heelo}/xk/${count}";
        String param = "#{key}_#{value}#{key}";
        String r = MatcherUtils.convertURI(source);
        String r1 = MatcherUtils.convertParam(param);
        System.out.println(r);
        System.out.println(r1);
        System.out.println(MatcherUtils.checkCount(param, MatcherUtils.PARAM_MATCH_PATTERN));
        String key = "83m";
        System.out.println(Pattern.compile("#\\{key\\}").matcher(param).replaceAll(key));;

    }

    @Test
    public void test02(){
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
    public void test01(){
        RedissonHelper helper = RedissonHelper.getInstance();

        for(int i=0; i<10; ++i) {
            System.out.println(helper.getIdForDate());
            System.out.println(helper.getId(0L, 1000L));
            System.out.println("--------------");
        }
    }
}

