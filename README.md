
# Webmind Framework

[![License](https://img.shields.io/badge/License-Apache-green)](https://raw.githubusercontent.com/dilldong/webmind/master/LICENSE)

Webmind is a lightweight web service framework that integrates with SpringFramework. Realize the embedded startup of Tomcat, which is easy to use, occupies less memory, responds quickly, and has stable service. Suitable for Java microservices and web projects.
## Add maven dependency
Add the following Maven dependency to your project's pom.xml
```xml
<dependency>
    <groupId>io.github.dilldong</groupId>
    <artifactId>webmind-framework</artifactId>
    <version>4.0.2</version>
</dependency>
```
## Example
### 1. Mapping
```java
@Controller
public class HelloController {
    
    @Mapping
    public String first() {
        return "Welcome usage mind-framework.";
    }

    // Spec a GET request, text response.
    @Mapping(value = "/request/text", method = RequestMethod.GET)
    public String withText() {
        return "Hello,This is mind-framework.";
    }

    // Json response
    @Mapping("/request/json")
    public String withJson() {
        return new Response<String>(HttpStatus.SC_OK, "OK").toJson();
    }

    // Json body response
    @Mapping("/request/json01")
    public String withJsonResult() {
        return new Response<Map<String, Object>>(HttpStatus.SC_OK, "OK")
                .setBody(ImmutableMap.of("name", "Smith", "age", 26, "gender", "Male"))
                .toJson();
    }

    // redirect
    @Mapping("/request/redirect")
    public String redirect() {
        return "redirect:https://github.com/dilldong";
    }

    // forward
    @Mapping("/request/forward")
    public String forward() {
        return "forward:https://github.com/dilldong";
    }

    // javascript response
    @Mapping("/request/js")
    public String js() {
        return "script:alert('This is JS window.');";
    }

    // velocity template engine
    @Mapping("/request/velocity")
    public Render velocity() {
        return new TemplateRender("/template/index.htm");
    }
}
```

### 2. Interceptor
```java
@Component
@Interceptor(value = {"/user/*"}, excludes = {"/login"})
public class InterceptorClass extends AbstractHandlerInterceptor {
    // Processing your business
}
```

### 3. Startup Application
```java
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(
                Application.class,
                new String[]{"spring/springContext.xml", "spring/businessConfig.xml"},
                args);
    }
}
```

## License
The Webmind Framework is released under version 2.0 of the Apache License.