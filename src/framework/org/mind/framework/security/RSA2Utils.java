package org.mind.framework.security;

import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.Strings;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * @author Marcus
 * @version 1.0
 */
public class RSA2Utils {
    private static final String ALGORITHM = "RSA";
    private static final String MODE = "RSA/ECB/PKCS1Padding";
    private static final String KEY_RSA_SIGN = "MD5withRSA";
    private static final int KEY_SIZE = 1024;

    public static RSA2 generateKey() {
        KeyPairGenerator keyGenerator;
        try {
            keyGenerator = KeyPairGenerator.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new DecoderException(e.getMessage(), e);
        }

        keyGenerator.initialize(KEY_SIZE);
        KeyPair keyPair = keyGenerator.generateKeyPair();
        return new RSA2(keyPair.getPublic().getEncoded(), keyPair.getPrivate().getEncoded());
    }


    /**
     * 公钥加密
     *
     * @param content
     * @param publicKey
     * @return Base64 String
     */
    public static String encrypt(String content, String publicKey) {
        return Strings.fromUTF8ByteArray(Base64.encode(encryptReturnBytes(content, publicKey)));
    }

    public static byte[] encryptReturnBytes(String content, String publicKey) {
        byte[] keys = Base64.decode(publicKey);
        byte[] data = content.getBytes(StandardCharsets.UTF_8);

        try {
            KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
            PublicKey pbKey = factory.generatePublic(new X509EncodedKeySpec(keys));
            Cipher cipher = Cipher.getInstance(MODE);
            cipher.init(Cipher.ENCRYPT_MODE, pbKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new DecoderException(e.getMessage(), e);
        }
    }

    /**
     * 私钥解密
     *
     * @param content
     * @param privateKey
     * @return
     */
    public static String decrypt(String content, String privateKey) {
        return Strings.fromUTF8ByteArray(decryptReturnBytes(content, privateKey));
    }

    public static byte[] decryptReturnBytes(String content, String privateKey) {
        byte[] keys = Base64.decode(privateKey);

        try {
            KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
            PrivateKey pvKey = factory.generatePrivate(new PKCS8EncodedKeySpec(keys));
            Cipher cipher = Cipher.getInstance(MODE);
            cipher.init(Cipher.DECRYPT_MODE, pvKey);
            return cipher.doFinal(Base64.decode(content));
        } catch (Exception e) {
            throw new DecoderException(e.getMessage(), e);
        }
    }

    /**
     * 私钥进行数据签名
     *
     * @param encryptedContent
     * @param privateKey       Base64 String
     * @return
     */
    public static String sign(String encryptedContent, String privateKey) {
        return Strings.fromUTF8ByteArray(Base64.encode(signReturnBytes(encryptedContent, privateKey)));
    }

    /**
     * 私钥进行数据签名
     *
     * @param encryptedContent
     * @param privateKey       Base64 String
     * @return
     */
    public static byte[] signReturnBytes(String encryptedContent, String privateKey) {
        byte[] data = encryptedContent.getBytes(StandardCharsets.UTF_8);
        byte[] keys = Base64.decode(privateKey.getBytes(StandardCharsets.UTF_8));

        try {
            KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
            PrivateKey pvKey = factory.generatePrivate(new PKCS8EncodedKeySpec(keys));
            Signature sign = Signature.getInstance(KEY_RSA_SIGN);
            sign.initSign(pvKey);
            sign.update(data);
            return sign.sign();
        } catch (Exception e) {
            throw new DecoderException(e.getMessage(), e);
        }
    }

    /**
     * 公钥进行签名验签
     *
     * @param encryptedContent encrypted content
     * @param publicKey        Base64 String
     * @param signStr
     * @return
     */
    @SneakyThrows
    public static boolean verify(String encryptedContent, String publicKey, String signStr) {
        byte[] keys = Base64.decode(publicKey.getBytes(StandardCharsets.UTF_8));
        byte[] data = encryptedContent.getBytes(StandardCharsets.UTF_8);

        KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
        PublicKey pbKey = factory.generatePublic(new X509EncodedKeySpec(keys));
        Signature signature = Signature.getInstance(KEY_RSA_SIGN);
        signature.initVerify(pbKey);
        signature.update(data);
        return signature.verify(Base64.decode(signStr.getBytes(StandardCharsets.UTF_8)));
    }

    public static class RSA2 {
        @Getter
        private final byte[] publics;
        @Getter
        private final byte[] privates;

        private String publicBase64;
        private String privateBase64;

        private String publicHex;
        private String privateHex;

        public RSA2(byte[] publics, byte[] privates) {
            this.publics = publics;
            this.privates = privates;
        }

        public String getPublicByBase64() {
            if (StringUtils.isEmpty(publicBase64))
                publicBase64 = Strings.fromUTF8ByteArray(Base64.encode(this.publics));
            return publicBase64;
        }

        public String getPrivateByBase64() {
            if (StringUtils.isEmpty(privateBase64))
                privateBase64 = Strings.fromUTF8ByteArray(Base64.encode(this.privates));
            return privateBase64;
        }

        public String getPublicByHex() {
            if (StringUtils.isEmpty(publicHex))
                publicHex = Strings.fromUTF8ByteArray(Hex.encode(this.publics));
            return publicHex;
        }

        public String getPrivateByHex() {
            if (StringUtils.isEmpty(privateHex))
                privateHex = Strings.fromUTF8ByteArray(Hex.encode(this.privates));
            return privateHex;
        }
    }
}
