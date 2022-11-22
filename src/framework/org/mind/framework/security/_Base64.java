package org.mind.framework.security;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * 是一个标准的Base64编码和转换的工具，为了迎合js的需要，
 * 故在此重复工作；当然，我们可以使用JDK API 自带的BASE64Decoder和BASE64Encoder
 * 进行编码和解码，但是就不能随意调整Base64的主要参数。
 *
 * @author dp
 * @date May 2, 2010
 */
final class _Base64 {

    private static final String KEY_STR = "Aa9Bb8Cc7Dd6Ee5Ff4Gg3Hh2Ii1Jj0Kk_Ll@Mm$NnOoPpQqRrSsTtUuVvWwXxYyZz";
//	private static final String KEY_STR = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@*=";

    private _Base64() {

    }

    private static class Base64Holder {
        private static final _Base64 instance = new _Base64();
    }

    public static _Base64 getInstance() {
        return Base64Holder.instance;
    }

    /**
     * 将输入的字符串按照Base64规则编码
     *
     * @param input
     * @return
     */
    public String encode(String input) {
        if (StringUtils.isEmpty(input))
            return input;

        byte[] b = input.getBytes(StandardCharsets.UTF_8);
        return this.encode(b);

    }

    /**
     * 遵循Base64规则,将一个字节数组进行编码。
     *
     * @param input byte array
     * @return
     */
    public String encode(byte[] input) {
        if (ArrayUtils.isEmpty(input))
            return null;

        StringBuilder buf = new StringBuilder();
        int b0, b1, b2, b3;
        int len = input.length;
        int i = 0;
        while (i < len) {
            byte tmp = input[i++];
            b0 = (tmp & 0xfc) >> 2;
            b1 = (tmp & 0x03) << 4;
            if (i < len) {
                tmp = input[i++];
                b1 |= (tmp & 0xf0) >> 4;
                b2 = (tmp & 0x0f) << 2;
                if (i < len) {
                    tmp = input[i++];
                    b2 |= (tmp & 0xc0) >> 6;
                    b3 = tmp & 0x3f;
                } else {
                    b3 = 64; // 1 byte "-" is supplement

                }
            } else {
                b2 = b3 = 64;// 2 bytes "-" are supplement
            }

            buf.append(KEY_STR.charAt(b0));
            buf.append(KEY_STR.charAt(b1));
            buf.append(KEY_STR.charAt(b2));
            buf.append(KEY_STR.charAt(b3));
        }
        return buf.toString();
    }

    /**
     * 解码一个字符串,遵循Base64规则。
     *
     * @param decodeStr 需要解码的字符串
     * @return
     */
    public String decode(String decodeStr) {
        byte[] b = decodeToByte(decodeStr);
        if (ArrayUtils.isEmpty(b))
            return null;

        return new String(b, StandardCharsets.UTF_8);
    }

    /**
     * 解码一个字符串到字节数组, 遵循Base64规则
     *
     * @param decodeStr
     * @return
     */
    public byte[] decodeToByte(String decodeStr) {
        if (StringUtils.isEmpty(decodeStr))
            return null;

        int len = decodeStr.length();
        byte[] b = new byte[(len / 4) * 3];
        int i = 0;
        int j = 0;
        int e = 0;
        int c;
        int tmp;

        while (i < len) {
            c = KEY_STR.indexOf(decodeStr.charAt(i++));
            tmp = c << 18;
            c = KEY_STR.indexOf(decodeStr.charAt(i++));
            tmp |= c << 12;
            c = KEY_STR.indexOf(decodeStr.charAt(i++));
            if (c < 64) {
                tmp |= c << 6;
                c = KEY_STR.indexOf(decodeStr.charAt(i++));
                if (c < 64) {
                    tmp |= c;
                } else {
                    e = 1;
                }
            } else {
                e = 2;
                ++i;
            }

            b[j + 2] = (byte) (tmp & 0xff);
            tmp >>= 8;
            b[j + 1] = (byte) (tmp & 0xff);
            tmp >>= 8;
            b[j] = (byte) (tmp & 0xff);
            j += 3;
        }

        if (e != 0) {
            len = b.length - e;
            byte[] copy = new byte[len];
            System.arraycopy(b, 0, copy, 0, len);
            return copy;
        }
        return b;
    }
}
