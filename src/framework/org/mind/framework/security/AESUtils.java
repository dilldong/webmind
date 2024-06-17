package org.mind.framework.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.bouncycastle.util.Strings;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * @author Marcus
 */
@Slf4j
public class AESUtils {
    static final String ALGORITHM = "AES";
    private static final String MODE = "AES/CBC/PKCS5Padding";

    /**
     * Sign the given content using the given key and iv.
     *
     * @param content sign content
     * @param key     base64 encoded key
     * @param iv      base64 encoded iv-key
     * @return a signed message from base64 encode
     */
    public static String encrypt4Base64(String content, String key, String iv) {
        byte[] bytes = encrypt(
                content,
                Base64.decode(key.getBytes(StandardCharsets.UTF_8)),
                Base64.decode(iv.getBytes(StandardCharsets.UTF_8)));

        return Strings.fromUTF8ByteArray(Base64.encode(bytes));
    }

    /**
     * Sign the given content using the given key and iv.
     *
     * @param content sign content
     * @param key     hex encoded key
     * @param iv      hex encoded iv-key
     * @return a signed message from hex encode
     */
    public static String encrypt4Hex(String content, String key, String iv) {
        byte[] keyBytes;
        byte[] ivBytes;
        try {
            keyBytes = Hex.decodeStrict(key);
            ivBytes = Hex.decodeStrict(iv);
        } catch (DecoderException e) {
            log.error(e.getMessage(), e);
            return null;
        }

        byte[] data = encrypt(content, keyBytes, ivBytes);
        return Strings.fromUTF8ByteArray(Hex.encode(data));
    }

    /**
     * Sign the given content using the given key and iv.
     *
     * @param content  sign content
     * @param keyBytes Base64 or Hex decode byte array
     * @param ivBytes  Base64 or Hex decode byte array
     * @return a signed message from byte array
     */
    public static byte[] encrypt(String content, byte[] keyBytes, byte[] ivBytes) {
        try {
            // 生成/读取用于加解密的密钥
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            //使用CBC模式，需要一个向量iv，可增加加密算法的强度
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

            // 指定算法、获取Cipher对象
            // AES/CBC/PKCS5Padding 算法/模式/补码方式
            Cipher cipher = Cipher.getInstance(MODE);

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            //进行最终的加解密操作
            return cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new DecoderException(e.getMessage(), e);
        }
    }

    /**
     * AES decryption after base64 decoding.
     *
     * @param content base64 encoded content
     * @param key     base64 encoded key
     * @param iv      base64 encoded iv-key
     * @return AES decrypted content
     */
    public static String decrypt4Base64(String content, String key, String iv) {
        return decrypt(
                Base64.decode(content.getBytes(StandardCharsets.UTF_8)),
                Base64.decode(key.getBytes(StandardCharsets.UTF_8)),
                Base64.decode(iv.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * AES decryption after hex decoding.
     *
     * @param content hex encoded content
     * @param key     hex encoded key
     * @param iv      hex encoded iv-key
     * @return AES decrypted content
     */
    public static String decrypt4Hex(String content, String key, String iv) {
        byte[] data = decrypt4HexReturnBytes(content, key, iv);
        if (Objects.isNull(data))
            return null;

        return Strings.fromUTF8ByteArray(data);
    }

    public static byte[] decrypt4HexReturnBytes(String content, String key, String iv) {
        byte[] contentBytes;
        byte[] keyBytes;
        byte[] ivBytes;
        try {
            contentBytes = Hex.decodeStrict(content);
            keyBytes = Hex.decodeStrict(key);
            ivBytes = Hex.decodeStrict(iv);
        } catch (org.bouncycastle.util.encoders.DecoderException e) {
            log.error(e.getMessage(), e);
            return null;
        }

        if (ObjectUtils.allNotNull(contentBytes, keyBytes, ivBytes))
            return decryptReturnBytes(contentBytes, keyBytes, ivBytes);

        return null;
    }

    /**
     * AES decryption
     *
     * @param contents content byte array
     * @param keyBytes key byte array
     * @param ivBytes  iv-key byte array
     * @return AES decrypted content
     */
    public static String decrypt(byte[] contents, byte[] keyBytes, byte[] ivBytes) {
        return Strings.fromUTF8ByteArray(decryptReturnBytes(contents, keyBytes, ivBytes));
    }

    public static byte[] decryptReturnBytes(byte[] contents, byte[] keyBytes, byte[] ivBytes) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            Cipher cipher = Cipher.getInstance(MODE);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return cipher.doFinal(contents);
        } catch (Exception e) {
            throw new DecoderException(e.getMessage(), e);
        }
    }


    /**
     * Get a key generator by 128 with base64 encode.
     *
     * @return base64 string
     */
    public static String generateKey() {
        return generateKeyBase64(128);
    }

    /**
     * Get a key generator with base64 encode.
     *
     * @param length 128/192/256
     * @return base64 string
     */
    public static String generateKeyBase64(int length) {
        return Strings.fromUTF8ByteArray(Base64.encode(generateBytes(length)));
    }

    /**
     * Get a key generator with Hex encode.
     *
     * @param length 128/192/256
     * @return hex string
     */
    public static String generateKeyHex(int length) {
        return Strings.fromUTF8ByteArray(Hex.encode(generateBytes(length)));
    }


    /**
     * Get a key generator
     *
     * @param length 128/192/256
     * @return byte array
     */
    public static byte[] generateBytes(int length) {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(length);
            SecretKey secretKey = keyGenerator.generateKey();

            return secretKey.getEncoded();
        } catch (NoSuchAlgorithmException e) {
            throw new DecoderException(e.getMessage(), e);
        }
    }

}
