
# Webmind Framework

[![License](https://img.shields.io/badge/License-Apache-green)](https://raw.githubusercontent.com/dilldong/webmind/master/LICENSE)

Webmind is a lightweight web service framework that integrates with SpringFramework. Realize the embedded startup of Tomcat, which is easy to use, occupies less memory, responds quickly, and has stable service. Suitable for Java microservices and web projects.

## Example
### 1.Controller
```java
@Controller
public class HelloController {
    
    @Mapping("/hello")
    public String hello() {
        return "Hello World!";
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
    public static void main(String[] args) throws Exception {
        SpringApplication.run(Application.class, args);
    }
}
```

## License
The Webmind Framework is released under version 2.0 of the Apache License.