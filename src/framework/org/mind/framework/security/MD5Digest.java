package org.mind.framework.security;

import org.apache.commons.lang3.StringUtils;
import org.mind.framework.exception.ThrowProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5Digest用来进行密码加密的md5公用参数
 *
 * @author dp
 */
public class MD5Digest {

    private static final Logger log = LoggerFactory.getLogger(MD5Digest.class);

    private static final _MD5 INSTANCE = _MD5.getInstance();

    private MD5Digest() {

    }

    /**
     * 基于jdk原生的md5加密方法
     *
     * @param input
     * @param charsetName
     * @return
     */
    public static byte[] md5Crypt(String input, String charsetName) {
        Charset charset = StringUtils.isEmpty(charsetName) ?
                StandardCharsets.UTF_8 : Charset.forName(charsetName);
        return md5Crypt(input, charset);
    }

    public static byte[] md5Crypt(String input, Charset charset) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            ThrowProvider.doThrow(e);
        }

        digest.update(input.getBytes(charset));
        return digest.digest();
    }

    /**
     * 自定义md5加密
     *
     * @param input
     * @return 加密后的字符串
     * @author dp
     */
    public static String md5Crypt(String input) {
        byte[] bytes = INSTANCE.md5Encrypt(input, StandardCharsets.UTF_8);
        return b2Hex(bytes);
    }

    /**
     * 把一个byte数组转换成十六进制的ASCII表示，
     * java中byte的toString无法实现这一点
     *
     * @author dp
     */
    public static String b2Hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(32);
        for (byte bt : bytes)
            sb.append(INSTANCE.byteHEX(bt));

        return sb.toString();
    }


    /**
     * b2iu是一个把byte数组按照不考虑正负号原则的＂升位＂运算，java没有unsigned运算
     *
     * @param bytes
     * @return String
     * @author dp
     */
    public static String b2iu(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte bt : bytes)
            sb.append(INSTANCE.b2iu(bt));

        return sb.toString();
    }

}
