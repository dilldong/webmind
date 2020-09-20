package org.mind.framework.security;

/**
 * 提供base64的编码和解码工具类
 *
 * @author dongping
 * @date May 2, 2010
 */
public class Base64Digest {

    /**
     * @param input String type
     * @return
     * @author dongping
     * @date May 2, 2010
     */
    public static String encode(String input) {
        return _Base64.getInstance().encode(input);
    }

    /**
     * @param input byte array
     * @return
     * @author dongping
     * @date May 2, 2010
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


    /**
     * 测试
     *
     * @param args
     * @author dongping
     * @date May 2, 2010
     */
//    public static void main(String[] args) {
//        String str = Base64Digest.decode("EgEt5BASeB7veT7z");
//        System.out.println(str);
//    }

}

