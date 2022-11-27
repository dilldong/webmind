package org.mind.framework.util;

import org.apache.commons.lang3.ArrayUtils;
import org.mind.framework.annotation.Mapping;

import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatcherUtils {

    /**
     * 默认匹配模式，区分大小写
     */
    public static final int DEFAULT_EQ = Pattern.CANON_EQ;

    public static final String URI_PARAM_MATCH = "(\\$\\{)\\w+\\}";

    public static final String PARAM_MATCH = "(#\\{)\\w+\\}";

    /**
     * 忽略大小写模式
     */
    public static final int IGNORECASE_EQ = Pattern.CASE_INSENSITIVE;

    public static Matcher matcher(String value, String regex, int... flags) {
        Pattern pattern = Pattern.compile(regex, ArrayUtils.isEmpty(flags) ? DEFAULT_EQ : flags[0]);
        return pattern.matcher(value);
    }

    /**
     * 验证表达式相等性
     *
     * @param value 匹配内容
     * @param regex 给定的正则表达式
     * @return 相等返回true，反之false
     * @author dp
     */
    public static boolean matcher(String value, String regex) {
        Matcher matcher = matcher(value, regex, DEFAULT_EQ);
        return matcher.matches();
    }


    /**
     * 检查值中某一个符号出现的个数，比如sql语句中出现"?"的个数.
     *
     * @param value 需要检查的值
     * @param regex 如：SQL---\\?+
     * @param flags 匹配模式，PatternUtil.IGNORECASE_EQ（忽略大小写的匹配）| PatternUtil.DEFAULT_EQ(默认等价匹配)
     * @return
     * @author dp
     */
    public static int checkCount(String value, String regex, int... flags) {
        Matcher matcher = matcher(value, regex, DEFAULT_EQ);
        int count = 0;
        while (matcher.find())
            count++;
        return count;
    }

    /**
     * 将{@link Mapping} value 转换为正则表达式
     *
     * @param uri {@link Mapping} value.
     * @return
     */
    public static String convertURI(String uri) {
        return toPattern(uri, URI_PARAM_MATCH);
    }

    public static String convertParam(String uri) {
        return toPattern(uri, PARAM_MATCH);
    }

    private static String toPattern(String param, String pattern) {
        StringJoiner joiner = new StringJoiner("");
        joiner.add("^");
        joiner.add(param.replaceAll(pattern, "([^\\/]+)")
                .replaceAll("\\*", "\\\\S*")
                .replaceAll("\\/", "\\\\/"));
        joiner.add("$");
        return joiner.toString();
    }
}
