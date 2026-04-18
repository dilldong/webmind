package org.mind.framework.helper;

import lombok.Getter;
import org.mind.framework.util.JsonUtils;

import java.io.Reader;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Java 的泛型在运行时会触发“类型擦除” (Type Erasure)，
 * 而 Gson 的 TypeToken 需要在编译时捕获确定的类型,
 * 在 Gson 新版本中, 更加强制了这一做法。
 * <p>底层逻辑：</p>
 * <p>TypeToken 的工作原理是创建一个匿名内部类，利用 getGenericSuperclass() 获取父类的泛型参数。
 *
 * @author Marcus
 * @version 1.0
 * @date 2026/4/18
 */
@Getter
public abstract class AbstractGsonBase<T> {
    private final Type type;

    protected AbstractGsonBase() {
        // 获取子类定义的具体泛型类型
        Type superClass = getClass().getGenericSuperclass();
        this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
    }

    public T fromJson(String json) {
        return JsonUtils.fromJson(json, type);
    }

    public T fromJson(Reader jsonReader) {
        return JsonUtils.fromJson(jsonReader, type);
    }
}
