package org.mind.framework;

/**
 * @author Marcus
 * @version 1.0
 */
public class Application {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(
                Application.class,
                new String[]{"spring/springContext.xml", "spring/businessConfig.xml"},
                new String[]{"frame.properties"},
                args);
    }


}
