package org.mind.framework.security;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * MurmurHash3 x64 128-bit implementation (Java).
 * 可直接用于生成 128-bit 哈希（byte[16] / hex / 两个 long）。
 * @version 1.0
 * @author Marcus
 * @date 2025/11/17
 */
public final class MurmurHash3 {
    private MurmurHash3(){}
    private static final long C1 = 0x87c37b91114253d5L;
    private static final long C2 = 0x4cf5ad432745937fL;

    /**
     * 计算字节数组的128位哈希值
     *
     * @param data 输入数据
     * @param seed 种子值
     * @return 128位哈希值（以两个long值表示）
     */
    public static HashCode128 hash128(byte[] data, int seed) {
        return hash128(data, 0, data.length, seed);
    }

    public static HashCode128 hash128(byte[] data) {
        return hash128(data, 0);
    }

    /**
     * 计算字节数组指定范围的128位哈希值
     *
     * @param data 输入数据
     * @param offset 起始偏移量
     * @param length 数据长度
     * @param seed 种子值
     * @return 128位哈希值
     */
    public static HashCode128 hash128(byte[] data, int offset, int length, int seed) {
        long h1 = seed & 0xFFFFFFFFL;
        long h2 = seed & 0xFFFFFFFFL;

        final int nblocks = length / 16;

        // 处理16字节的块
        for (int i = 0; i < nblocks; i++) {
            int index = offset + i * 16;
            long k1 = getLongLittleEndian(data, index);
            long k2 = getLongLittleEndian(data, index + 8);

            h1 ^= mixK1(k1);
            h1 = Long.rotateLeft(h1, 27);
            h1 += h2;
            h1 = h1 * 5 + 0x52dce729;

            h2 ^= mixK2(k2);
            h2 = Long.rotateLeft(h2, 31);
            h2 += h1;
            h2 = h2 * 5 + 0x38495ab5;
        }

        // 处理剩余字节
        long k1 = 0;
        long k2 = 0;
        int index = offset + nblocks * 16;
        int remaining = length - nblocks * 16;

        switch (remaining) {
            case 15: k2 ^= ((long) data[index + 14] & 0xff) << 48;
            case 14: k2 ^= ((long) data[index + 13] & 0xff) << 40;
            case 13: k2 ^= ((long) data[index + 12] & 0xff) << 32;
            case 12: k2 ^= ((long) data[index + 11] & 0xff) << 24;
            case 11: k2 ^= ((long) data[index + 10] & 0xff) << 16;
            case 10: k2 ^= ((long) data[index + 9] & 0xff) << 8;
            case 9:  k2 ^= ((long) data[index + 8] & 0xff);
                h2 ^= mixK2(k2);
            case 8:  k1 ^= ((long) data[index + 7] & 0xff) << 56;
            case 7:  k1 ^= ((long) data[index + 6] & 0xff) << 48;
            case 6:  k1 ^= ((long) data[index + 5] & 0xff) << 40;
            case 5:  k1 ^= ((long) data[index + 4] & 0xff) << 32;
            case 4:  k1 ^= ((long) data[index + 3] & 0xff) << 24;
            case 3:  k1 ^= ((long) data[index + 2] & 0xff) << 16;
            case 2:  k1 ^= ((long) data[index + 1] & 0xff) << 8;
            case 1:  k1 ^= ((long) data[index] & 0xff);
                h1 ^= mixK1(k1);
        }

        // 最终混合
        h1 ^= length;
        h2 ^= length;

        h1 += h2;
        h2 += h1;

        h1 = fmix64(h1);
        h2 = fmix64(h2);

        h1 += h2;
        h2 += h1;

        return new HashCode128(h1, h2);
    }

    /**
     * 计算字符串的128位哈希值
     */
    public static HashCode128 hash128(String input) {
        return hash128(input, 0);
    }

    public static HashCode128 hash128(String input, int seed) {
        return hash128(input.getBytes(StandardCharsets.UTF_8), seed);
    }

    private static long getLongLittleEndian(byte[] data, int index) {
        return ((long) data[index] & 0xff) |
                (((long) data[index + 1] & 0xff) << 8) |
                (((long) data[index + 2] & 0xff) << 16) |
                (((long) data[index + 3] & 0xff) << 24) |
                (((long) data[index + 4] & 0xff) << 32) |
                (((long) data[index + 5] & 0xff) << 40) |
                (((long) data[index + 6] & 0xff) << 48) |
                (((long) data[index + 7] & 0xff) << 56);
    }

    private static long mixK1(long k1) {
        k1 *= C1;
        k1 = Long.rotateLeft(k1, 31);
        k1 *= C2;
        return k1;
    }

    private static long mixK2(long k2) {
        k2 *= C2;
        k2 = Long.rotateLeft(k2, 33);
        k2 *= C1;
        return k2;
    }

    private static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }

    /**
     * 128位哈希值的封装类
     */
    public record HashCode128(long h1, long h2) {

        /**
         * 转换为字节数组（16字节）
         */
        public byte[] asBytes() {
            ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putLong(h1);
            buffer.putLong(h2);
            return buffer.array();
        }

        /**
         * 转换为十六进制字符串
         */
        public String asHex() {
            return String.format("%016x%016x", h1, h2);
        }

        @Override
        public String toString() {
            return asHex();
        }

        @Override
        public int hashCode() {
                return (int) (h1 ^ h2);
            }
    }
}
