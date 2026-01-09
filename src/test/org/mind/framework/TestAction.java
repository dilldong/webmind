package org.mind.framework;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.mind.framework.annotation.Mapping;
import org.mind.framework.http.Response;
import org.mind.framework.web.Action;
import org.mind.framework.web.renderer.Render;
import org.mind.framework.web.renderer.TemplateRender;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * @author Marcus
 * @version 1.0
 */
@Slf4j
@Controller
public class TestAction {

    @Mapping(value = {"/", "/index"})
    public String first() {
        return "Welcome usage mind-framework.";
    }

    @Mapping(value = "/request/text", method = RequestMethod.GET)
    public String withText() {
        return "Hello, This is mind-framework.";
    }

    @Mapping(value = "/request/value/${type}", method = RequestMethod.GET)
    public String withText(String type) {
        return String.format("Hello,This is mind-framework. %s", type);
    }

    @Mapping(value = "/request/int/${type}", method = RequestMethod.GET)
    public String withText(int type) {
        return String.format("Hello,This is mind-framework. %d", type);
    }

    @Mapping("/request/json")
    public String withJson() {
        return new Response<String>(HttpServletResponse.SC_OK, "OK").toJson();
    }

    @Mapping("/request/json01")
    public String withJsonResult() {
        Map<String, Object> dataMap = Map.of(
                "name", "Smith",
                "age", 26,
                "gender", "Male",
                "session-id", Action.getActionContext().getSession().getId()
        );

        return new Response<Map<String, Object>>(HttpServletResponse.SC_OK, "OK")
                .setResult(dataMap)
                .toJson();
    }

    @Mapping("/request/redirect")
    public String redirect() {
        return "redirect:https://github.com/dilldong/webmind";
    }

    @Mapping("/request/forward")
    public String forward() {
        return "forward:/";
    }

    @Mapping("/request/js")
    public String js() {
        return "script:alert('This is JS window.');";
    }

    @Mapping("/request/velocity")
    public Render velocity() {
        return new TemplateRender(
                "index.vm",
                "listItem",
                Arrays.asList(11, 22, 32, 3, 62, 92));
    }

    @Mapping("/number/${value}")
    public int number(int value) {
        return value;
    }

    @Mapping("/list")
    public List<String> list() {
        List<String> results = new ArrayList<>();
        IntStream.range(0, 10).forEachOrdered(item -> results.add("List-" + item));
        return results;
    }

    @Mapping("/map")
    public Map<String, Object> map() {
        Map<String, Object> results = new HashMap<>();
        IntStream.range(0, 10).forEachOrdered(item -> results.put("K-" + item, item));
        return results;
    }

    @Mapping("/object")
    public TestSpringModule.A object() {
        return TestSpringModule.A.builder()
                .field01("field01")
                .num01(100)
                .num02(99_100_200_000L)
                .build();
    }

}
