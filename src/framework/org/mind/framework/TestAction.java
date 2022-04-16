package org.mind.framework;

import org.mind.framework.annotation.Mapping;
import org.springframework.stereotype.Controller;

import java.util.concurrent.TimeUnit;

/**
 * @author Marcus
 * @version 1.0
 */
@Controller
public class TestAction {

    @Mapping(value = "/request")
    public String request() {
        try {
            TimeUnit.SECONDS.sleep(15L);
        } catch (InterruptedException e) {
        }

        return "00.hello,world!";
    }

    @Mapping(value = "/req1")
    public String request1() {
        try {
            TimeUnit.SECONDS.sleep(10L);
        } catch (InterruptedException e) {
        }

        return "01.hello,world!";
    }
}
