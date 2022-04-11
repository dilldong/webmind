package org.mind.framework;

import org.mind.framework.server.WebServer;
import org.springframework.boot.SpringApplication;

/**
 * @author Marcus
 * @version 1.0
 */
public class Application {

    public static void main(String[] args) throws Exception {
        System.out.println("class-run: "+ Application.class.getResource("/").getPath());
        String webPath = "file:/Users/Ping/Desktop/mind-framework-3.0.0.jar!/BOOT-INF/web";

        WebServer webServer = new WebServer()
                .addPreResources("/BOOT-INF/classes", Application.class.getResource("/").getPath(), "/")
                .addPreResources("/BOOT-INF", webPath, "/");

        System.out.println("getBaseDir: "+ webServer.getBaseDir());
        webServer.setBaseDir(webPath.substring(0, webPath.lastIndexOf('/')));
//        webServer.setWebXml(new File("web/WEB-INF/web.xml").getAbsolutePath());

        SpringApplication.run()
        webServer.setWebApp(false);
        webServer.startServer();
    }


}
