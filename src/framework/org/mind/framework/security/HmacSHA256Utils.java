package org.mind.framework.security;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

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
        return new String(Base64.encode(bytes), StandardCharsets.UTF_8);
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
        return new String(Hex.encode(bytes), StandardCharsets.UTF_8);
    }

    /**
     * Sign the given message using the given secret.
     *
     * @param message   message to sign
     * @param secretKey secret key
     * @return a signed message from byte array
     */
    public static byte[] encrypt(String message, String secretKey) {
        try {
            Mac sha256Hmac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            sha256Hmac.init(secretKeySpec);

            return sha256Hmac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new DecoderException(e.getMessage(), e);
        }
    }

    /**
     * Hex encode the SecretKey
     *
     * @param password
     * @param salt           random letters
     * @param iterationCount the iteration count.
     * @param keyLength      the to-be-derived key length. ranges: 128/192/256
     * @return hex string
     */
    public static String generateKeyHex(String password, String salt, int iterationCount, int keyLength) {
        byte[] bytes = generateKey(password, salt, iterationCount, keyLength);
        return new String(Hex.encode(bytes), StandardCharsets.UTF_8);
    }

    /**
     * Base64 encode the SecretKey
     *
     * @param password
     * @param salt           random letters
     * @param iterationCount the iteration count.
     * @param keyLength      the to-be-derived key length. ranges: 128/192/256
     * @return base64 string
     */
    public static String generateKeyBase64(String password, String salt, int iterationCount, int keyLength) {
        byte[] bytes = generateKey(password, salt, iterationCount, keyLength);
        return new String(Base64.encode(bytes), StandardCharsets.UTF_8);
    }

    /**
     * Using password, salt, iteration count, and key length for generating PBEKey of variable-key-size PBE ciphers.
     *
     * @param password
     * @param salt           random letters
     * @param iterationCount the iteration count.
     * @param keyLength      the to-be-derived key length. ranges: 128/192/256
     * @return byte array
     */
    public static byte[] generateKey(String password, String salt, int iterationCount, int keyLength) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_HMAC_SHA256);
            KeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    salt.getBytes(StandardCharsets.UTF_8),
                    iterationCount,
                    keyLength);

            SecretKey originalKey = new SecretKeySpec(
                    factory.generateSecret(spec).getEncoded(),
                    AESUtils.ALGORITHM);

            return originalKey.getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new DecoderException(e.getMessage(), e);
        }
    }
}
