package org.mind.framework.util;

import org.apache.commons.lang3.RandomStringUtils;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Random code tools
 *
 * @author marcus
 * @version 1.1
 */
public final class RandomCodeUtil {

    private RandomCodeUtil() {
    }

    private static class Helper {
        private static final AtomicReference<Integer> RANDOM_REF;
        private static final Function<Integer, UnaryOperator<Integer>> OPERATOR_FUNCTION;

        static {
            RANDOM_REF = new AtomicReference<>(-1);
            OPERATOR_FUNCTION = (bound) -> (prev) -> {
                // ThreadLocalRandom效率更高，每个线程有一个独立的随机数生成器，用于并发产生随机数，能够解决多个线程发生的竞争争夺
                ThreadLocalRandom tlRandom = ThreadLocalRandom.current();
                int innerValue = tlRandom.nextInt(bound);
                while (innerValue == prev && bound > 1)// should different from last random
                    innerValue = tlRandom.nextInt(bound);
                return innerValue;
            };
        }
    }

    public static int nextThreadLocalRandom(int bound) {
        return Helper.RANDOM_REF.updateAndGet(Helper.OPERATOR_FUNCTION.apply(bound));
    }

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
     *
     * @param letters true: contains characters
     * @param numbers true: contains numbers
     */
    public static String getRandomString(int length, boolean letters, boolean numbers) {
        return RandomStringUtils.random(length, letters, numbers);
    }

    public static int getRandomNums(int max, int min) {
        return (int) Math.floor(Math.random() * (max - min) + min);
    }


}
