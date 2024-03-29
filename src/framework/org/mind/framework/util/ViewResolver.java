package org.mind.framework.util;

import org.mind.framework.http.Response;
import org.mind.framework.web.renderer.BinaryRender;
import org.mind.framework.web.renderer.FileRenderer;
import org.mind.framework.web.renderer.JavaScriptRender;
import org.mind.framework.web.renderer.NullRender;
import org.mind.framework.web.renderer.Render;
import org.mind.framework.web.renderer.RenderType;
import org.mind.framework.web.renderer.TemplateRender;
import org.mind.framework.web.renderer.TextRender;
import org.springframework.http.HttpStatus;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 *
 */
public class ViewResolver {
    protected ViewResolver() {
    }

    public static <V> Response<V> response() {
        return new Response<>();
    }

    public static <V> Response<V> response(int code, String msg) {
        return new Response<>(code, msg);
    }

    public static <V> Response<V> response(int code, String msg, V result) {
        return new Response<>(code, msg, result);
    }

    public static <V> Response<V> response(HttpStatus state) {
        return ViewResolver.response(state.value(), state.getReasonPhrase());
    }

    public static <V> Response<V> response(HttpStatus state, V result) {
        return ViewResolver.response(state.value(), state.getReasonPhrase(), result);
    }

    public static Render template(String templatePath) {
        return new TemplateRender(templatePath);
    }

    public static Render template(String templatePath, Map<String, Object> modelMap) {
        return new TemplateRender(templatePath, modelMap);
    }

    public static Render template(String templatePath, String key, Object value) {
        return new TemplateRender(templatePath, key, value);
    }

    public static Render js(String javascript) {
        return new JavaScriptRender(javascript, StandardCharsets.UTF_8.name());
    }

    public static Render file(String filePath) {
        return new FileRenderer(filePath);
    }

    public static Render file(File file) {
        return new FileRenderer(file);
    }

    public static Render binary(byte[] bytes) {
        return new BinaryRender(bytes);
    }

    public static Render text(String text) {
        return new TextRender(text, StandardCharsets.UTF_8.name());
    }

    public static Render render(String uri, RenderType type) {
        return new NullRender(uri, type);
    }

    public static String redirect(String path) {
        return RenderType.REDIRECT.keyName + path;
    }

    public static String forward(String path) {
        return RenderType.FORWARD.keyName + path;
    }
}
