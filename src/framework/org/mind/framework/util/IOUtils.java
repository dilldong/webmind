package org.mind.framework.util;

import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.util.Strings;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class IOUtils {

    public static final String DIR_SEPARATOR = "/";

    public static final String DOT_SEPARATOR = ".";

    private IOUtils() {
    }

    public static int byte2int(byte[] b, int offset) {
        return b[offset + 3] & 0xff | (b[offset + 2] & 0xff) << 8
                | (b[offset + 1] & 0xff) << 16 | (b[offset] & 0xff) << 24;
    }

    public static int byte2int(byte[] b) {
        return b[3] & 0xff | (b[2] & 0xff) << 8 | (b[1] & 0xff) << 16
                | (b[0] & 0xff) << 24;
    }

    public static long byte2long(byte[] b) {
        return (long) b[7] & (long) 255 | ((long) b[6] & (long) 255) << 8
                | ((long) b[5] & (long) 255) << 16
                | ((long) b[4] & (long) 255) << 24
                | ((long) b[3] & (long) 255) << 32
                | ((long) b[2] & (long) 255) << 40
                | ((long) b[1] & (long) 255) << 48 | (long) b[0] << 56;
    }

    public static long byte2long(byte[] b, int offset) {
        return (long) b[offset + 7] & (long) 255
                | ((long) b[offset + 6] & (long) 255) << 8
                | ((long) b[offset + 5] & (long) 255) << 16
                | ((long) b[offset + 4] & (long) 255) << 24
                | ((long) b[offset + 3] & (long) 255) << 32
                | ((long) b[offset + 2] & (long) 255) << 40
                | ((long) b[offset + 1] & (long) 255) << 48
                | (long) b[offset] << 56;
    }

    public static byte[] int2byte(int n) {
        byte[] b = new byte[4];
        b[0] = (byte) (n >> 24);
        b[1] = (byte) (n >> 16);
        b[2] = (byte) (n >> 8);
        b[3] = (byte) n;
        return b;
    }

    public static void int2byte(int n, byte[] buf, int offset) {
        buf[offset] = (byte) (n >> 24);
        buf[offset + 1] = (byte) (n >> 16);
        buf[offset + 2] = (byte) (n >> 8);
        buf[offset + 3] = (byte) n;
    }

    public static byte[] short2byte(int n) {
        byte[] b = new byte[2];
        b[0] = (byte) (n >> 8);
        b[1] = (byte) n;
        return b;
    }

    public static void short2byte(int n, byte[] buf, int offset) {
        buf[offset] = (byte) (n >> 8);
        buf[offset + 1] = (byte) n;
    }

    public static byte[] long2byte(long n) {
        byte[] b = new byte[8];
        b[0] = (byte) (int) (n >> 56);
        b[1] = (byte) (int) (n >> 48);
        b[2] = (byte) (int) (n >> 40);
        b[3] = (byte) (int) (n >> 32);
        b[4] = (byte) (int) (n >> 24);
        b[5] = (byte) (int) (n >> 16);
        b[6] = (byte) (int) (n >> 8);
        b[7] = (byte) (int) n;
        return b;
    }

    public static void long2byte(long n, byte[] buf, int offset) {
        buf[offset] = (byte) (int) (n >> 56);
        buf[offset + 1] = (byte) (int) (n >> 48);
        buf[offset + 2] = (byte) (int) (n >> 40);
        buf[offset + 3] = (byte) (int) (n >> 32);
        buf[offset + 4] = (byte) (int) (n >> 24);
        buf[offset + 5] = (byte) (int) (n >> 16);
        buf[offset + 6] = (byte) (int) (n >> 8);
        buf[offset + 7] = (byte) (int) n;
    }

    public static long getLong(byte[] b, int off) {
        return (b[off + 7] & 0xFFL) + ((b[off + 6] & 0xFFL) << 8)
                + ((b[off + 5] & 0xFFL) << 16) + ((b[off + 4] & 0xFFL) << 24)
                + ((b[off + 3] & 0xFFL) << 32) + ((b[off + 2] & 0xFFL) << 40)
                + ((b[off + 1] & 0xFFL) << 48) + ((b[off] & 0xFFL) << 56);
    }

    public static String readString(InputStream in) throws IOException {
        return readString(readBytes(in));
    }

    public static String readString(InputStream in, int size) throws IOException {
        return readString(readBytes(in, size));
    }

    public static String readString(InputStream in, long size) throws IOException {
        return readString(readBytes(in, size));
    }

    public static String readString(byte[] b) {
        return readString(b, 0);
    }

    public static String readString(byte[] b, int offset) {
        return readString(b, offset, b.length);
    }

    public static String readString(byte[] b, int offset, int len) {
        return Strings.fromUTF8ByteArray(b, offset, len);
    }

    public static byte[] readBytes(InputStream in) throws IOException {
        return org.apache.commons.io.IOUtils.toByteArray(in);
    }

    public static byte[] readBytes(InputStream in, int size) throws IOException {
        return org.apache.commons.io.IOUtils.toByteArray(in, size);
    }

    public static byte[] readBytes(InputStream in, long size) throws IOException {
        return org.apache.commons.io.IOUtils.toByteArray(in, size);
    }

    /**
     * Convert an integer to a byte array in MSB first order
     *
     * @param num The number to store
     * @param len The length of the integer to convert
     * @return An array of length len containing the byte representation of num.
     */
    public static byte[] intToBytes(int num, int len) {
        return (intToBytes(num, len, null, 0));
    }

    /**
     * Convert an integer to a byte array in MSB first order. This method exists
     * as well as the <code>longToBytes</code> method for performance reasons.
     * More often than not, a 4-byte value is the largest being
     * converted...doing that using <code>ints</code> instead of
     * <code>longs</code> will offer a slight performance increase. The code
     * for the two methods is identical except for using ints instead of longs
     * to hold mask, shiftwidth and number values.
     *
     * @param num    The number to store
     * @param len    The length of the integer to convert (that is, the number of
     *               bytes to generate).
     * @param b      the byte array to write the integer to.
     * @param offset the offset in <code>b</code> to write the integer to.
     * @return An array of length len containing the byte representation of num.
     */
    public static byte[] intToBytes(int num, int len, byte[] b, int offset) {
        if (Objects.isNull(b)) {
            b = new byte[len];
            offset = 0;
        }
        int sw = ((len - 1) * 8);
        int mask = (0xff << sw);

        for (int i = 0; i < len; ++i) {
            b[offset + i] = (byte) ((num & mask) >>> sw);

            sw -= 8;
            mask >>>= 8;
        }

        return (b);
    }

    /**
     * Convert a long to a byte array in MSB first order.
     *
     * @param num The number to store
     * @param len The length of the integer to convert (that is, the number of
     *            bytes to generate).
     * @return An array of length len containing the byte representation of num.
     */
    public static byte[] longToBytes(long num, int len) {
        return longToBytes(num, len, null, 0);
    }

    /**
     * Convert a long to a byte array in MSB first order.
     *
     * @param num    The number to store
     * @param len    The length of the integer to convert (that is, the number of
     *               bytes to generate).
     * @param b      the byte array to write the integer to.
     * @param offset the offset in <code>b</code> to write the integer to.
     * @return An array of length len containing the byte representation of num.
     */
    public static byte[] longToBytes(long num, int len, byte[] b, int offset) {
        if (Objects.isNull(b)) {
            b = new byte[len];
            offset = 0;
        }
        long sw = ((len - 1) * 8L);
        long mask = (0xffL << sw);

        for (int i = 0; i < len; ++i) {
            b[offset + i] = (byte) ((num & mask) >>> sw);

            sw -= 8;
            mask >>>= 8;
        }

        return (b);
    }

    /**
     * Convert a byte array (or part thereof) into an integer. The byte array
     * should be in big-endian form. That is, the byte at index 'offset' should
     * be the MSB.
     *
     * @param b      The array containing the bytes
     * @param offset The array index of the MSB
     * @param size   The number of bytes to convert into the integer
     * @return An integer value represented by the specified bytes.
     */
    public static int bytesToInt(byte[] b, int offset, int size) {
        int num = 0;
        int sw = 8 * (size - 1);

        for (int loop = 0; loop < size; loop++) {
            num |= ((int) b[offset + loop] & 0x00ff) << sw;
            sw -= 8;
        }

        return (num);
    }

    /**
     * Convert a byte array (or part thereof) into a long. The byte array should
     * be in big-endian form. That is, the byte at index 'offset' should be the
     * MSB.
     *
     * @param b      The array containing the bytes
     * @param offset The array index of the MSB
     * @param size   The number of bytes to convert into the long
     * @return A long value represented by the specified bytes.
     */
    public static long bytesToLong(byte[] b, int offset, int size) {
        long num = 0;
        long sw = 8L * ((long) size - 1L);

        for (int loop = 0; loop < size; loop++) {
            num |= ((long) b[offset + loop] & 0x00ff) << sw;
            sw -= 8;
        }

        return (num);
    }

    public static String encodeHex(byte[] data) {
        if (ArrayUtils.isEmpty(data))
            return "";

        StringBuilder buf = new StringBuilder(data.length << 1);

        for (byte datum : data) {
            if (((int) datum & 0xff) < 0x10) {
                buf.append("0");
            }
            buf.append(Long.toString((int) datum & 0xff, 16));
        }
        return buf.toString();
    }

    public static byte[] hexToBytes(String ss) {
        byte[] bytes = new byte[ss.length() / 2];
        char[] chars = ss.toLowerCase().toCharArray();
        int byteCount = 0;
        for (int i = 0; i < chars.length; i += 2) {
            byte newByte = 0x00;
            newByte |= hexCharToByte(chars[i]);
            newByte <<= 4;
            newByte |= hexCharToByte(chars[i + 1]);
            bytes[byteCount] = newByte;
            byteCount++;
        }
        return bytes;
    }

    private static byte hexCharToByte(char ch) {
        switch (ch) {
            case '0':
                return 0x00;
            case '1':
                return 0x01;
            case '2':
                return 0x02;
            case '3':
                return 0x03;
            case '4':
                return 0x04;
            case '5':
                return 0x05;
            case '6':
                return 0x06;
            case '7':
                return 0x07;
            case '8':
                return 0x08;
            case '9':
                return 0x09;
            case 'a':
                return 0x0A;
            case 'b':
                return 0x0B;
            case 'c':
                return 0x0C;
            case 'd':
                return 0x0D;
            case 'e':
                return 0x0E;
            case 'f':
                return 0x0F;
            default:
                break;
        }
        return 0x00;
    }
}
