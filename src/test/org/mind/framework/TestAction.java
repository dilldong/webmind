package org.mind.framework;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.mind.framework.annotation.Mapping;
import org.mind.framework.http.Response;
import org.mind.framework.renderer.Render;
import org.mind.framework.renderer.TemplateRender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.PostConstruct;
import java.util.Map;

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

    @Mapping
    public String first() {
        return "Welcome usage mind-framework.";
    }

    @Mapping(value = "/request/text", method = RequestMethod.GET)
    public String withText() {
        return "Hello,This is mind-framework.";
    }

    @Mapping("/request/json")
    public String withJson() {
        return new Response<String>(HttpStatus.SC_OK, "OK").toJson();
    }

    @Mapping("/request/json01")
    public String withJsonResult() {
        return new Response<Map<String, Object>>(HttpStatus.SC_OK, "OK")
                .setBody(ImmutableMap.of("name", "Smith", "age", 26, "gender", "Male"))
                .toJson();
    }

    @Mapping("/request/redirect")
    public String redirect() {
        return "redirect:https://github.com/dilldong";
    }

    @Mapping("/request/forward")
    public String forward() {
        return "forward:https://github.com/dilldong";
    }

    @Mapping("/request/js")
    public String js() {
        return "script:alert('This is JS window.');";
    }

    @Mapping("/request/velocity")
    public Render velocity() {
        return new TemplateRender("/template/index.htm");
    }
}
