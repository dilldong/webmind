package org.mind.framework.security;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Base62编解码工具类
 * Base62使用字符集：0-9, a-z, A-Z (共62个字符)
 * 常用于生成短链接、唯一ID等场景
 *
 * @version 1.0
 * @author Marcus
 * @date 2025/6/13
 */
public class Base62Utils {
    // Base62字符集：0-9, a-z, A-Z
    private static final String BASE62_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = 62;

    /**
     * 将长整型数字编码为Base62字符串
     *
     * @param value 要编码的数字
     * @return Base62编码后的字符串
     */
    public static String encode(long value) {
        if (value == 0)
            return "0";

        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.append(BASE62_CHARS.charAt((int) (value % BASE)));
            value /= BASE;
        }

        return sb.reverse().toString();
    }

    /**
     * 将Base62字符串解码为长整型数字
     *
     * @param base62String Base62编码的字符串
     * @return 解码后的数字
     * @throws IllegalArgumentException 如果输入包含非法字符
     */
    public static long decodeToLong(String base62String) {
        if (StringUtils.isEmpty(base62String))
            throw new IllegalArgumentException("The input string cannot be empty");

        long result = 0;
        long power = 1;

        // 从右到左处理字符
        for (int i = base62String.length() - 1; i >= 0; --i) {
            char c = base62String.charAt(i);
            int index = BASE62_CHARS.indexOf(c);

            if (index == -1)
                throw new IllegalArgumentException("Illegal char: " + c);

            result += index * power;
            power *= BASE;
        }

        return result;
    }

    /**
     * 将字节数组编码为Base62字符串
     *
     * @param bytes 要编码的字节数组
     * @return Base62编码后的字符串
     */
    public static String encode(byte[] bytes) {
        if (ArrayUtils.isEmpty(bytes))
            return StringUtils.EMPTY;

        // 将字节数组转换为大整数
        BigInteger bigInt = new BigInteger(1, bytes);
        return encode(bigInt);
    }

    /**
     * 将Base62字符串解码为字节数组
     *
     * @param base62String Base62编码的字符串
     * @return 解码后的字节数组
     */
    public static byte[] decodeToBytes(String base62String) {
        if (StringUtils.isEmpty(base62String))
            return new byte[0];

        BigInteger bigInt = decodeToBigInteger(base62String);
        return bigInt.toByteArray();
    }

    /**
     * 将大整数编码为Base62字符串
     *
     * @param bigInt 要编码的大整数
     * @return Base62编码后的字符串
     */
    public static String encode(BigInteger bigInt) {
        if (BigInteger.ZERO.compareTo(bigInt) == 0)
            return "0";

        StringBuilder sb = new StringBuilder();
        BigInteger base = BigInteger.valueOf(BASE);

        while (bigInt.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divmod = bigInt.divideAndRemainder(base);
            sb.append(BASE62_CHARS.charAt(divmod[1].intValue()));
            bigInt = divmod[0];
        }

        return sb.reverse().toString();
    }

    /**
     * 将Base62字符串解码为大整数
     *
     * @param base62String Base62编码的字符串
     * @return 解码后的大整数
     */
    public static BigInteger decodeToBigInteger(String base62String) {
        if (StringUtils.isEmpty(base62String))
            throw new IllegalArgumentException("The input string cannot be empty");

        BigInteger result = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(BASE);
        BigInteger power = BigInteger.ONE;

        // 从右到左处理字符
        for (int i = base62String.length() - 1; i >= 0; --i) {
            char c = base62String.charAt(i);
            int index = BASE62_CHARS.indexOf(c);

            if (index == -1)
                throw new IllegalArgumentException("Illegal char: " + c);

            result = result.add(BigInteger.valueOf(index).multiply(power));
            power = power.multiply(base);
        }

        return result;
    }

    /**
     * 将字符串编码为Base62
     *
     * @param str 要编码的字符串
     * @return Base62编码后的字符串
     */
    public static String encode(String str) {
        if (StringUtils.isEmpty(str))
            return StringUtils.EMPTY;

        return encode(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将Base62字符串解码为原始字符串
     *
     * @param base62String Base62编码的字符串
     * @return 解码后的原始字符串
     */
    public static String decodeToString(String base62String) {
        if (StringUtils.isEmpty(base62String))
            return StringUtils.EMPTY;

        byte[] bytes = decodeToBytes(base62String);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 生成指定长度的随机Base62字符串
     *
     * @param length 字符串长度
     * @return 随机的Base62字符串
     */
    public static String randomString(int length) {
        if (length <= 0)
            return StringUtils.EMPTY;

        StringBuilder sb = new StringBuilder(length);
        Random random = new Random();

        for (int i = 0; i < length; ++i) {
            sb.append(BASE62_CHARS.charAt(random.nextInt(BASE)));
        }

        return sb.toString();
    }

    /**
     * 验证字符串是否为有效的Base62格式
     *
     * @param str 要验证的字符串
     * @return 如果是有效的Base62格式返回true，否则返回false
     */
    public static boolean isValidBase62(String str) {
        if (StringUtils.isEmpty(str))
            return false;

        for (char c : str.toCharArray()) {
            if (BASE62_CHARS.indexOf(c) == -1)
                return false;
        }

        return true;
    }

}
