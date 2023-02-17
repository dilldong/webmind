package org.mind.framework;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.mind.framework.helper.RedissonHelper;
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
        String json = "{\n" +
                "  \"id\": 64,\n" +
                "  \"jsonrpc\": \"2.0\",\n" +
                "  \"method\": \'eth_getBlockByNumber\',\n" +
                "  \"params\": [\n" +
                "    \"0x1b03d33\",\n" +
                "    true\n" +
                "  ]\n" +
                "}";

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

