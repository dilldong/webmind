package org.mind.framework.security;

import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
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

    @SneakyThrows
    public static RSA2 generateKey() {
        KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance(ALGORITHM);
        keyGenerator.initialize(KEY_SIZE);
        KeyPair keyPair = keyGenerator.generateKeyPair();
        return
                RSA2.builder()
                        .publics(keyPair.getPublic().getEncoded())
                        .privates(keyPair.getPrivate().getEncoded())
                        .build();
    }


    /**
     * 公钥加密
     * @param content
     * @param publicKey
     * @return
     */
    @SneakyThrows
    public static String encrypt(String content, String publicKey) {
        byte[] publics = Base64.decodeBase64(publicKey);
        byte[] data = content.getBytes(StandardCharsets.UTF_8);

        KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
        PublicKey pbKey = factory.generatePublic(new X509EncodedKeySpec(publics));
        Cipher cipher = Cipher.getInstance(MODE);
        cipher.init(Cipher.ENCRYPT_MODE, pbKey);
        return Base64.encodeBase64String(cipher.doFinal(data));
    }

    /**
     * 私钥解密
     * @param content
     * @param privateKey
     * @return
     */
    @SneakyThrows
    public static String decrypt(String content, String privateKey) {
        byte[] privates = Base64.decodeBase64(privateKey);

        KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
        PrivateKey pvKey = factory.generatePrivate(new PKCS8EncodedKeySpec(privates));
        Cipher cipher = Cipher.getInstance(MODE);
        cipher.init(Cipher.DECRYPT_MODE, pvKey);
        return new String(cipher.doFinal(Base64.decodeBase64(content)));
    }

    /**
     * 私钥进行数据签名
     *
     * @param encryptedContent
     * @param privateKey
     * @return
     */
    @SneakyThrows
    public static String sign(String encryptedContent, String privateKey) {
        byte[] data = encryptedContent.getBytes(StandardCharsets.UTF_8);
        byte[] privates = Base64.decodeBase64(privateKey);

        KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
        PrivateKey pvKey = factory.generatePrivate(new PKCS8EncodedKeySpec(privates));
        Signature sign = Signature.getInstance(KEY_RSA_SIGN);
        sign.initSign(pvKey);
        sign.update(data);
        return Base64.encodeBase64String(sign.sign());
    }

    /**
     * 公钥进行签名验签
     * @param encryptedContent
     * @param publicKey
     * @param signStr
     * @return
     */
    @SneakyThrows
    public static boolean verify(String encryptedContent, String publicKey, String signStr) {
        byte[] publics = Base64.decodeBase64(publicKey);
        byte[] data = encryptedContent.getBytes(StandardCharsets.UTF_8);

        KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
        PublicKey pbKey = factory.generatePublic(new X509EncodedKeySpec(publics));
        Signature signature = Signature.getInstance(KEY_RSA_SIGN);
        signature.initVerify(pbKey);
        signature.update(data);
        return signature.verify(Base64.decodeBase64(signStr));
    }

    public static void main(String[] args) {
        RSA2 rsa = RSA2Utils.generateKey();
        String orig = "dsakldfhkj*^=~圣诞节";
        String encdata = RSA2Utils.encrypt(orig, rsa.getPublicByBase64());
        String decdata = RSA2Utils.decrypt(encdata, rsa.getPrivateByBase64());
        System.out.println(rsa);
        System.out.println(encdata);
        System.out.println(decdata);

        String signStr = RSA2Utils.sign(encdata, rsa.getPrivateByBase64());
        System.out.println("签名: " + signStr);

        System.out.println("签名验证: " + RSA2Utils.verify(encdata, rsa.getPublicByBase64(), signStr));
    }


    @Builder
    public static class RSA2 {
        @Getter
        private byte[] publics;
        @Getter
        private byte[] privates;

        public String getPublicByBase64() {
            return Base64.encodeBase64String(this.publics);
        }

        public String getPrivateByBase64() {
            return Base64.encodeBase64String(this.privates);
        }

        @Override
        public String toString() {
            return new ToStringBuilder(RSA2.this, ToStringStyle.SHORT_PREFIX_STYLE)
                    .append("public-key: ").append(getPublicByBase64())
                    .append("private-key: ").append(getPrivateByBase64())
                    .toString();
        }
    }
}
