package org.mind.framework.util;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.mind.framework.annotation.Mapping;
import org.springframework.web.cors.CorsConfiguration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatcherUtils {
    private static final String URL_SCHEMA_SEP = "://";
    private static final String URL_SEP = "([^\\/]+?)";
    private static final String ANY_CHAR = "\\\\S*";
    private static final String URI_SEP = "\\\\/";

    private static final String DOMAIN_DOT = "\\\\.";
    private static final String DOMAIN_PREFIX = "^(https?:\\/\\/)?";
    private static final String DOMAIN_ANY = "(?:[a-zA-Z0-9_-]+\\\\.)*";
    private static final String DOMAIN_ANY_END = "(\\/[\\S]*)?$";

    public static final String START = "^";
    public static final String END = "$";

    /**
     * Default match pattern, case-sensitive.
     */
    public static final int DEFAULT_EQ = Pattern.CANON_EQ;

    /**
     * ignore case
     */
    public static final int IGNORECASE_EQ = Pattern.CASE_INSENSITIVE;

    public static final Pattern URI_PARAM_PATTERN = Pattern.compile("(\\$\\{)[^/]+?\\}");

    public static final Pattern PARAM_MATCH_PATTERN = Pattern.compile("(#\\{)[^/]+?\\}");

    public static final Pattern ANY_PATTERN = Pattern.compile("\\*");

    public static final Pattern REPLACE_DOMAIN_SEP = Pattern.compile("\\.");

    public static final Pattern URI_SEP_PATTERN = Pattern.compile("\\/");

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
     * @param flags 匹配模式，MatcherUtils.IGNORECASE_EQ（忽略大小写的匹配）| MatcherUtils.DEFAULT_EQ(默认等价匹配)
     * @return
     * @author dp
     */
    public static int checkCount(String value, String regex, int... flags) {
        Matcher matcher = matcher(value, regex, flags);
        int count = 0;
        while (matcher.find())
            ++count;
        return count;
    }

    public static int checkCount(String value, Pattern pattern) {
        Matcher matcher = pattern.matcher(value);
        int count = 0;
        while (matcher.find())
            ++count;
        return count;
    }

    public static boolean matchURL(String urlWithWildcard, String searchUrl) {
        int search = urlWithWildcard.indexOf(URL_SCHEMA_SEP);
        if (search > -1)
            urlWithWildcard = urlWithWildcard.substring(search + 3);

        int anyIndex = urlWithWildcard.indexOf(CorsConfiguration.ALL);
        if (anyIndex > -1) {
            // Check the '*' back is '.'
            if(IOUtils.DOT_SEPARATOR.equals(urlWithWildcard.substring(anyIndex + 1, anyIndex + 2)))
                urlWithWildcard = urlWithWildcard.substring(0, anyIndex + 1) + urlWithWildcard.substring(anyIndex + 2);

            String regex = REPLACE_DOMAIN_SEP.matcher(urlWithWildcard).replaceAll(DOMAIN_DOT);
            regex = String.join(
                    StringUtils.EMPTY,
                    DOMAIN_PREFIX,
                    MatcherUtils.ANY_PATTERN.matcher(regex).replaceAll(DOMAIN_ANY),
                    DOMAIN_ANY_END);
            return MatcherUtils.matcher(searchUrl, regex, MatcherUtils.IGNORECASE_EQ).matches();
        }

        return Strings.CI.contains(searchUrl, urlWithWildcard);
    }

    /**
     * 将{@link Mapping} value 转换为正则表达式
     *
     * @param uri {@link Mapping} value.
     * @return
     */
    public static String convertURI(String uri) {
        return toPattern(uri, URI_PARAM_PATTERN);
    }

    public static String convertURIIfExists(String uri) {
        if (MatcherUtils.checkCount(uri, URI_PARAM_PATTERN) > 0)
            return MatcherUtils.convertURI(uri);

        return uri;
    }

    public static String convertParam(String uri) {
        return toPattern(uri, PARAM_MATCH_PATTERN);
    }

    private static String toPattern(String param, Pattern pattern) {
        String newParam = pattern.matcher(param).replaceAll(URL_SEP);
        newParam = ANY_PATTERN.matcher(newParam).replaceAll(ANY_CHAR);
        newParam = URI_SEP_PATTERN.matcher(newParam).replaceAll(URI_SEP);

        return START + newParam + END;
    }

}
