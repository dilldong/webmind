package org.mind.framework;

import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.Test;
import org.mind.framework.http.Response;
import org.mind.framework.util.DateUtils;
import org.mind.framework.util.JsonUtils;
import org.mind.framework.util.ViewResolver;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @version 1.0
 * @author Marcus
 */
public class JsonTest {

    @Test
    public void test03() {
        A<List<Object>> a = new A<>(23, "dasef", Arrays.asList("value1", "value2", 0.00, DateUtils.currentMillis()));
        String json = ViewResolver.<A>response(HttpStatus.OK, a).toJson(false);
        System.out.println(json);

        Response<A> resp = JsonUtils.fromJson(json, new TypeToken<Response<A>>(){});
        System.out.println(resp);
        System.out.println(resp.toJson());

    }

    @Test
    public void test02() {
        A<String> a = new A<>(23, "dasef", "valueof-A");
        String json = JsonUtils.toJson(a, A.class);
        System.out.println("A toJson: " + json);

        a = JsonUtils.fromJson(json, A.class);
        System.out.println("A fromJson: " + a);

        Map<String, Object> aMap = JsonUtils.fromJson(json, Map.class);
        System.out.println("A->Map<String, Object> fromJson: ");
        aMap.forEach((k, v) -> System.out.println(k + ": " + v));

        A<List<Integer>> a1 = new A<>(56, "ndi", Arrays.asList(9328));
        String as = JsonUtils.toJson(new A[]{a, a1}, A[].class);

        System.out.println();
        System.out.println("A[]-> toJson: " + as);

        System.out.println();
        System.out.println("A[]->Array fromJson: " + Arrays.toString(JsonUtils.fromJson(as, A[].class)));

        List<A> fromList = JsonUtils.fromJson(as, new TypeToken<List<A>>(){});
        System.out.println();
        System.out.println("A[]->List fromJson: ");
        fromList.forEach(System.out::println);


        //List<Map<String, JsonElement>> fromMapList01 = JsonUtils.fromJson(as, new TypeToken<List<Map<String, JsonElement>>>(){});

        List<Map<String, Object>> fromMapList = JsonUtils.fromJson(as, List.class);
        System.out.println();
        System.out.println("A[]->List<Map<String, Object>> fromJson: ");
        fromMapList.forEach(list -> {
            list.forEach((k, v) -> System.out.println(k + ": " + v));
        });
    }

    @Test
    public void test01() {
        String json = JsonUtils.toJson(Arrays.asList("dasd", "23"), List.class);
        System.out.println("toJson: " + json);

        String[] array = JsonUtils.fromJson(json, String[].class);
        System.out.println("toArray: " + Arrays.toString(array));
    }

    @Data
    @AllArgsConstructor
    class A<T> {
        @Expose
        int age;
        String name;
        T value;
    }
}
