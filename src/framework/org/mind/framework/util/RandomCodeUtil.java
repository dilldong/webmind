package org.mind.framework.util;

import org.apache.commons.lang3.RandomStringUtils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Random code tools
 * @author marcus
 * @version 1.1
 */
public final class RandomCodeUtil {

    public static int random4Digit() {
        return (int) (1_000 + (9_999 - 1_000) * Math.abs(ThreadLocalRandom.current().nextDouble()));
    }

    public static int random5Digit() {
        return (int) (10_000 + (99_999 - 10_000) * Math.abs(ThreadLocalRandom.current().nextDouble()));
    }

    public static int random6Digit() {
        return (int) (100_000 + (999_999 - 100_000) * Math.abs(ThreadLocalRandom.current().nextDouble()));
    }

    public static int random7Digit() {
        return (int) (1_000_000 + (9_999_999 - 1_000_000) * Math.abs(ThreadLocalRandom.current().nextDouble()));
    }

    public static int random8Digit() {
        return (int) (10_000_000 + (99_999_999 - 10_000_000) * Math.abs(ThreadLocalRandom.current().nextDouble()));
    }


    /**
     * A random string of numbers or characters of the specified length
     * @param length
     * @param letters true: contains characters
     * @param numbers true: contains numbers
     * @return
     */
    public static String getRandomString(int length, boolean letters, boolean numbers) {
        return RandomStringUtils.random(length, letters, numbers);
    }

    public static int getRandomNums(int max, int min) {
        return (int) Math.floor(Math.random() * (max - min) + min);
    }


}
