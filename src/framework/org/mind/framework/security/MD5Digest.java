package org.mind.framework.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;

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
     * @param charset
     * @return
     */
    public static byte[] md5Crypt(String input, String charset) {
        if (charset == null || "".equals(charset))
            charset = "utf-8";

        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(input.getBytes(charset));
            return digest.digest();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public static final String encodeHex(byte[] bytes) {
        StringBuffer sb = new StringBuffer(bytes.length << 1);
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
     * @author dongping
     */
    public static String md5Crypt(String input) {
        return _MD5.getInstance().md5Encrypt(input);
    }

    /**
     * 把一个byte数组转换成十六进制的ASCII表示，
     * java中byte的toString无法实现这一点
     *
     * @author dongping
     */
    public static String b2Hex(byte[] bytes) {
        StringBuffer sb = new StringBuffer(bytes.length << 1);
        for (byte bt : bytes)
            sb.append(b2Hex(bt));

        return sb.toString();
    }

    /**
     * 把一个byte类型的数转换成十六进制的ASCII表示，
     * java中byte的toString无法实现这一点
     *
     * @author dongping
     */
    public static String b2Hex(byte b) {
        return _MD5.getInstance().byteHEX(b);
    }

    /**
     * b2iu是一个把byte数组按照不考虑正负号的原则的＂升位＂程序，因为java没有unsigned运算
     *
     * @param bytes
     * @return String
     * @author dongping
     */
    public static String b2iu(byte[] bytes) {
        StringBuffer sb = new StringBuffer(bytes.length * 3);
        for (byte bt : bytes)
            sb.append(b2iu(bt));

        return sb.toString();
    }

    /**
     * b2iu是一个把byte按照不考虑正负号的原则的＂升位＂程序，因为java没有unsigned运算
     *
     * @param b
     * @return long
     * @author dongping
     */
    public static long b2iu(byte b) {
        return _MD5.getInstance().b2iu(b);
    }
}
