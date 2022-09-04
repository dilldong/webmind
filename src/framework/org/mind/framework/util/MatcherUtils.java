package org.mind.framework.util;

import org.mind.framework.annotation.Mapping;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatcherUtils {

    /**
     * 默认匹配模式，区分大小写
     */
    public static final int DEFAULT_EQ = Pattern.CANON_EQ;

    public static final String URI_PARAM_MATCH = "(\\$\\{)[A-Za-z_]+\\}";

    /**
     * 忽略大小写模式
     */
    public static final int IGNORECASE_EQ = Pattern.CASE_INSENSITIVE;

    public static Matcher matcher(String value, String regex, int... flags) {
        Pattern pattern = Pattern.compile(regex, flags.length == 0 ? DEFAULT_EQ : flags[0]);
        Matcher matcher = pattern.matcher(value);
        return matcher;
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
        StringBuilder sb = new StringBuilder();
        sb.append("^");
        sb.append(
                uri.replaceAll("(\\$\\{)[A-Za-z_]+\\}", "([^\\/]+)")
                        .replaceAll("\\*", "\\\\S*")
                        .replaceAll("\\/", "\\\\/"));
        sb.append("$");

        return sb.toString();
    }

//    public static void main(String[] args) {
//        String v = "/user/${id}";
//
//        String regex = convertURI(v);
//        System.out.println(regex);
//
//        int count = MatcherUtils.checkCount(v, "(\\$\\{)[A-Za-z_]+\\}");
//        System.out.println("count:" + count);
//
//        String uri = "/user/1332?id=13&path=http://www.c.cs/user/idja/tt";
//        boolean f = MatcherUtils.matcher(uri, regex);
//        System.out.println(f);
//
//
//        String res = "css|js|jpg|png|gif|html|htm|xls|xlsx|doc|docx|ppt|pptx|pdf|rar|zip|txt";
//
//        f = MatcherUtils.matcher("TXT", res, IGNORECASE_EQ).matches();
//        System.out.println("=====" + f);
//
//    }

}
