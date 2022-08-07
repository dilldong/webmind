package org.mind.framework.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

/**
 * Utility class to sign messages using HMAC-SHA256.
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
     * @return a signed message
     */
    public static String hmacSha256(String message, String secret) {
        try {
            Mac sha256Hmac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), HMAC_SHA256);
            sha256Hmac.init(secretKeySpec);

//            return Hex.encodeHexString(sha256Hmac.doFinal(message.getBytes()));
            return Base64.encodeBase64String(sha256Hmac.doFinal(message.getBytes()));
        } catch (Exception e) {
            log.warn("Unable to sign message.");
            log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Define a method for generating the SecretKey from a given password with 12,288(or more) iterations and a key length bits:
     *
     * @param password
     * @param salt     random letters
     * @param length   128/192/256
     * @return
     */
    public static String getKeyByPBKDF2(String password, String salt, int length) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_HMAC_SHA256);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), 12_288, length);
            SecretKey originalKey = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");

//            return Hex.encodeHexString(originalKey.getEncoded());
            return Base64.encodeBase64String(originalKey.getEncoded());
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            log.warn("Unable to sign message.");
            log.error(e.getMessage(), e);
        }

        return null;
    }
}
