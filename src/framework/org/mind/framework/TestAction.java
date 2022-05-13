package org.mind.framework;

import lombok.extern.slf4j.Slf4j;
import org.mind.framework.annotation.Mapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;

import javax.annotation.PostConstruct;

/**
 * @author Marcus
 * @version 1.0
 */
@Slf4j
@Controller
public class TestAction {

    @Value("${db.connectionTimeout}")
    String value;

    @PostConstruct
    private void _init() {
        log.info("======:{}", value);
    }

    @Mapping()
    public String first() {
        return "First";
    }

    @Mapping("/request")
    public String request() {
        return "00.hello,world!";
    }

    @Mapping("/req1")
    public String request1() {
        return "01.hello,world!";
    }
}
