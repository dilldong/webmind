package org.mind.framework.security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.SecureRandom;
import java.security.Security;
import java.util.Objects;

/**
 * @author marcus
 * @version 1.0
 * @date 2026/1/15
 */
public final class SunCrypto {
    /*
     * 线程安全的随机数生成器
     *
     * 在大多数现代 JVM 上已经足够安全（使用 /dev/urandom）
     * 这里不再使用ThreadLocal包装(除非每秒百万次调用)
     */
    public static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SunCrypto() {
    }

    static {
        // 使用BouncyCastleProvider
        if (Objects.isNull(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)))
            Security.addProvider(new BouncyCastleProvider());
    }

}
