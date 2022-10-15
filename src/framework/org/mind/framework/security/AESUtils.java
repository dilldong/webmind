package org.mind.framework.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;

/**
 * @author Marcus
 */
@Slf4j
public class AESUtils {

    private static final String ALGORITHM = "AES";
    private static final String MODE = "AES/CBC/PKCS5Padding";

    public static String encrypt(String content, String key, String iv) {
        byte[] contentData = content.getBytes();
        byte[] keyData = Base64.decodeBase64(key);
        byte[] ivData = Base64.decodeBase64(iv);

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
            byte[] result = cipher.doFinal(contentData);

            // 对加密后的字节数组进行Base64编码
            return Base64.encodeBase64String(result);

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return null;
    }

    public static String descrypt(String content, String key, String iv) {
        byte[] contentData = Base64.decodeBase64(content);
        byte[] keyData = Base64.decodeBase64(key);
        byte[] ivData = Base64.decodeBase64(iv);

        try {
            SecretKeySpec keySpec = new SecretKeySpec(keyData, ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(ivData);
            Cipher cipher = Cipher.getInstance(MODE);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] data = cipher.doFinal(contentData);
            return new String(data);

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Get a key generator by 128
     *
     * @return
     */
    public static String generateKey() {
        return generateKey(128);
    }

    /**
     * Get a key generator
     * @param length 128/192/256
     * @return
     */
    public static String generateKey(int length) {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(length);
            SecretKey secretKey = keyGenerator.generateKey();

            return Base64.encodeBase64String(secretKey.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

}
