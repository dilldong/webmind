package org.mind.framework.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Objects;

/**
 * Utility class to sign messages using HMAC-SHA256.
 *
 * @author marcus
 */
@Slf4j
public class HmacSHA256Utils {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String PBKDF2_HMAC_SHA256 = "PBKDF2WithHmacSHA256";

    /**
     * Sign the given message using the given secret.
     *
     * @param message message to sign
     * @param secret  secret key
     * @return a signed message from encode base64
     */
    public static String encryptBase64(String message, String secret) {
        byte[] bytes = encrypt(message, secret);
        if (Objects.isNull(bytes))
            return null;

        return Base64.encodeBase64String(bytes);
    }

    /**
     * Sign the given message using the given secret.
     *
     * @param message message to sign
     * @param secret  secret key
     * @return a signed message from encode hex
     */
    public static String encryptHex(String message, String secret) {
        byte[] bytes = encrypt(message, secret);
        if (Objects.isNull(bytes))
            return null;

        return Hex.encodeHexString(bytes);
    }

    /**
     * Sign the given message using the given secret.
     *
     * @param message message to sign
     * @param secretKey  secret key
     * @return a signed message from byte array
     */
    public static byte[] encrypt(String message, String secretKey) {
        try {
            Mac sha256Hmac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            sha256Hmac.init(secretKeySpec);

            return sha256Hmac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Hex encode the SecretKey
     *
     * @param password
     * @param salt     random letters
     * @param length   128/192/256
     * @return hex string
     */
    public static String generateKeyHex(String password, String salt, int length) {
        byte[] bytes = generateKey(password, salt, length);
        if (Objects.isNull(bytes))
            return null;

        return Hex.encodeHexString(bytes);
    }

    /**
     * Base64 encode the SecretKey
     *
     * @param password
     * @param salt     random letters
     * @param length   128/192/256
     * @return base64 string
     */
    public static String generateKeyBase64(String password, String salt, int length) {
        byte[] bytes = generateKey(password, salt, length);
        if (Objects.isNull(bytes))
            return null;

        return Base64.encodeBase64String(bytes);
    }

    /**
     * Define a method for generating the SecretKey from a given password with 12,288(or more) iterations and a key length bits:
     *
     * @param password
     * @param salt     random letters
     * @param length   128/192/256
     * @return byte array
     */
    public static byte[] generateKey(String password, String salt, int length) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_HMAC_SHA256);
            KeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    salt.getBytes(StandardCharsets.UTF_8),
                    12_288,
                    length);

            SecretKey originalKey = new SecretKeySpec(
                    factory.generateSecret(spec).getEncoded(),
                    AESUtils.ALGORITHM);

            return originalKey.getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            log.error(e.getMessage(), e);
        }

        return null;
    }
}
