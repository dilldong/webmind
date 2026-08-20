package org.mind.framework.config;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.mind.framework.ContextSupport;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;

/**
 * @author: Marcus
 * @date: 2026/1/21
 * @version: 1.0
 */
@Slf4j
public class TestSpringContextInitializer implements ApplicationContextInitializer<GenericApplicationContext> {
    @Override
    public void initialize(@NotNull GenericApplicationContext applicationContext) {
        log.info("Spring context init ....");
        ContextSupport.setApplicationContext(applicationContext);
    }
}
