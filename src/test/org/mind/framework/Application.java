package org.mind.framework;

import org.mind.framework.config.AppConfiguration;
import org.springframework.context.annotation.Import;

/**
 * @author Marcus
 * @version 1.0
 */
@Import(AppConfiguration.class)
public class Application {

    public static void main(String[] args) {
//        SpringApplication.run(
//                new String[]{"spring/springContext.xml", "spring/businessConfig.xml"},
//                args);

        SpringApplication.run(Application.class, args);
    }


}
