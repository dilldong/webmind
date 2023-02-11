package org.mind.framework.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;

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

    public static String encryptBase64(String content, String key, String iv) {
        byte[] bytes = encrypt(content, key, iv);
        if (Objects.isNull(bytes))
            return null;

        return Base64.encodeBase64String(bytes);
    }

    public static String encryptHex(String content, String key, String iv) {
        byte[] bytes = encrypt(content, key, iv);
        if (Objects.isNull(bytes))
            return null;

        return Hex.encodeHexString(bytes);
    }

    public static byte[] encrypt(String content, String key, String iv) {
        byte[] contentData = content.getBytes(StandardCharsets.UTF_8);
        byte[] keyData = Base64.decodeBase64(key.getBytes(StandardCharsets.UTF_8));
        byte[] ivData = Base64.decodeBase64(iv.getBytes(StandardCharsets.UTF_8));

        try {
            // 生成/读取用于加解密的密钥
            SecretKeySpec keySpec = new SecretKeySpec(keyData, ALGORITHM);

            //使用CBC模式，需要一个向量iv，可增加加密算法的强度
            IvParameterSpec ivSpec = new IvParameterSpec(ivData);

            // 指定算法、获取Cipher对象
            // AES/CBC/PKCS5Padding 算法/模式/补码方式
            Cipher cipher = Cipher.getInstance(MODE);

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            //进行最终的加解密操作
            return cipher.doFinal(contentData);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return null;
    }

    public static String descrypt(String content, String key, String iv) {
        byte[] contentData = Base64.decodeBase64(content.getBytes(StandardCharsets.UTF_8));
        byte[] keyData = Base64.decodeBase64(key.getBytes(StandardCharsets.UTF_8));
        byte[] ivData = Base64.decodeBase64(iv.getBytes(StandardCharsets.UTF_8));

        try {
            SecretKeySpec keySpec = new SecretKeySpec(keyData, ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(ivData);
            Cipher cipher = Cipher.getInstance(MODE);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] data = cipher.doFinal(contentData);
            return new String(data, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
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
        byte[] bytes = generateBytes(length);
        if (Objects.isNull(bytes))
            return null;

        return Base64.encodeBase64String(bytes);
    }

    /**
     * Get a key generator with Hex encode.
     *
     * @param length 128/192/256
     * @return hex string
     */
    public static String generateKeyHex(int length) {
        byte[] bytes = generateBytes(length);
        if (Objects.isNull(bytes))
            return null;

        return Hex.encodeHexString(bytes);
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
            log.error(e.getMessage(), e);
        }
        return null;
    }

}
