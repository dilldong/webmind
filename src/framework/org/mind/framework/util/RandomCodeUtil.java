package org.mind.framework.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机码工具类
 */
public final class RandomCodeUtil {

    private static final Logger logger = LoggerFactory.getLogger(RandomCodeUtil.class);

    private static final String RANDOM_REGEX = "\\d{6}";

    public static int randomNum() {
        return (int) (1000 + (9999 - 1000) * Math.abs(ThreadLocalRandom.current().nextDouble()));
    }

    public static boolean isRandomNum(String code) {
        return MatcherUtils.matcher(code, RANDOM_REGEX);
    }


    /**
     * 取输入长度的随机数
     *
     * @param length
     * @return
     */
    public static String getRandomNums(int length) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < length; i++)
            str.append(random.nextInt(10));
        return str.toString();
    }

    public static int getRandomNums(int max, int min) {
        int ran2 = (int) Math.floor(Math.random() * (max - min) + min);
        return ran2;
    }

    public static void main(String[] args) {
        System.out.println(randomNum());
        System.out.println(isRandomNum("32_2"));
        System.out.println(getRandomNums(2));

        System.out.println(getRandomNums(190, 200));
    }

}
