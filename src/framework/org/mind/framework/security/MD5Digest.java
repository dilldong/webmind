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


    public static String encodeHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length << 1);
        for (byte b : bytes) {
            if ((b & 0xff) < 16)
                sb.append("0");
            sb.append(Long.toString(b & 0xff, 16));
        }

        return sb.toString();
    }

    /**
     * 自定义md5加密，直接返回加密后的结果
     *
     * @param input
     * @return
     * @author dp
     */
    public static String md5Crypt(String input) {
        return _MD5.getInstance().md5Encrypt(input, StandardCharsets.UTF_8);
    }

    /**
     * 把一个byte数组转换成十六进制的ASCII表示，
     * java中byte的toString无法实现这一点
     *
     * @author dp
     */
    public static String b2Hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length << 1);
        for (byte bt : bytes)
            sb.append(b2Hex(bt));

        return sb.toString();
    }

    /**
     * 把一个byte类型的数转换成十六进制的ASCII表示，
     * java中byte的toString无法实现这一点
     *
     * @author dp
     */
    public static String b2Hex(byte b) {
        return _MD5.getInstance().byteHEX(b);
    }

    /**
     * b2iu是一个把byte数组按照不考虑正负号的原则的＂升位＂程序，因为java没有unsigned运算
     *
     * @param bytes
     * @return String
     * @author dp
     */
    public static String b2iu(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte bt : bytes)
            sb.append(b2iu(bt));

        return sb.toString();
    }

    /**
     * b2iu是一个把byte按照不考虑正负号的原则的＂升位＂程序，因为java没有unsigned运算
     *
     * @param b
     * @return long
     * @author dp
     */
    public static long b2iu(byte b) {
        return _MD5.getInstance().b2iu(b);
    }
}
