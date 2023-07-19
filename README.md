
# Webmind Framework

[![License](https://img.shields.io/github/license/dilldong/webmind)](https://raw.githubusercontent.com/dilldong/webmind/master/LICENSE)
[![Maven2.0](https://img.shields.io/badge/maven-build-blue)](https://mvnrepository.com/artifact/io.github.dilldong/webmind-framework)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/e457baf96038467f814c72d0300eda44)](https://app.codacy.com/gh/dilldong/webmind/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)

Webmind is a lightweight web service framework that integrates with SpringFramework. Realize the embedded startup of Tomcat, which is easy to use, occupies less memory, responds quickly, and has stable service. Suitable for Java microservices and web projects.
## Maven
Add the following Maven dependency to your project's pom.xml
```xml
<dependency>
    <groupId>io.github.dilldong</groupId>
    <artifactId>webmind-framework</artifactId>
    <version>4.5.2</version>
</dependency>
```
## Gradle
```text
implementation 'io.github.dilldong:webmind-framework:4.5.2'
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
    
    // Spec a URL parameter, text response.
    @Mapping(value = "/request/text/${name}", method = RequestMethod.GET)
    public String withText(String name) {
        return "Hello "+ name +",This is mind-framework.";
    }

    // Json response
    @Mapping("/request/json")
    public Response<String> withJson() {
        return new Response<String>(HttpStatus.SC_OK, "OK");
    }

    // Json body response
    @Mapping("/request/json01")
    public String withJsonResult() {
        return new Response<Map<String, Object>>(HttpStatus.SC_OK, "OK")
                .setResult(ImmutableMap.of("name", "Smith", "age", 26, "gender", "Male"))
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
@Import(AppConfiguration.class)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```
### or
```java
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(
                new String[]{"spring/springContext.xml"},
                args);
    }
}
```

## License
The Webmind Framework is released under version 2.0 of the Apache License.