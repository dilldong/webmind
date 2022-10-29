package org.mind.framework.security;

/**
 * 提供base64的编码和解码工具类
 *
 * @author dp
 * @date May 2, 2010
 */
public class Base64Digest {

    /**
     * @param input String type
     * @return
     */
    public static String encode(String input) {
        return _Base64.getInstance().encode(input);
    }

    /**
     * @param input byte array
     * @return
     */
    public static String encode(byte[] input) {
        return _Base64.getInstance().encode(input);
    }

    public static String decode(String decodeStr) {
        return _Base64.getInstance().decode(decodeStr);
    }

    public static byte[] decodeToArray(String decodeStr) {
        return _Base64.getInstance().decodeToByte(decodeStr);
    }
}

