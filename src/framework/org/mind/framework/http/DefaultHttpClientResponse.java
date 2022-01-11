package org.mind.framework.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ContentType;
import org.mind.framework.util.JsonUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * 支持Apache HttpClient get/post请求
 *
 * @author Ping
 */
public class DefaultHttpClientResponse<T> extends HttpResponse<T> {

    private Charset charset = StandardCharsets.UTF_8;

    private CloseableHttpResponse httpResponse;

    public DefaultHttpClientResponse(CloseableHttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        super.responseCode = httpResponse.getStatusLine().getStatusCode();
        HttpEntity entity = httpResponse.getEntity();
        ContentType contentType = ContentType.getOrDefault(entity);

        if (contentType.getCharset() != null)
            charset = contentType.getCharset();

        try {
            if (entity.getContentEncoding() != null) {
                super.inStream = new GZIPInputStream(entity.getContent());
                log.info("{} = {}", entity.getContentEncoding().getName(), entity.getContentEncoding().getValue());
            } else {
                super.inStream = entity.getContent();
            }
        } catch (UnsupportedOperationException e) {
            log.error(e.getMessage(), e);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public Header[] getAllHeaders() {
        return this.httpResponse.getAllHeaders();
    }

    @Override
    public String asString() {
        return super.asString(charset);
    }

    @Override
    public T asJson() {
        return super.asJson(charset);
    }

    public Map<String, JsonElement> formatJson() {
        // gson int format double
        Gson gson = new GsonBuilder().setDateFormat(JsonUtils.DEFAULT_DATE_PATTERN)
                .registerTypeAdapter(ArrayList.class, new JsonDeserializer<Map<String, JsonElement>>() {
                    @Override
                    public Map<String, JsonElement> deserialize(JsonElement json, Type typeof,
                                                                JsonDeserializationContext context) throws JsonParseException {

                        Map<String, JsonElement> resultMap = new HashMap<>();

                        if (json.isJsonObject()) {
                            JsonObject jsonObject = json.getAsJsonObject();
                            Set<Map.Entry<String, JsonElement>> entrySet = jsonObject.entrySet();
                            for (Map.Entry<String, JsonElement> entry : entrySet) {
                                resultMap.put(entry.getKey(), entry.getValue());
                            }

                        } else if (json.isJsonArray()) {
                            throw new IllegalStateException("Json对象是一个数组，请使用formatJsonList()");
                        }
                        return resultMap;
                    }
                }).create();

        return gson.fromJson(
                asString(),
                new TypeToken<Map<String, JsonElement>>() {
                }.getType());
    }


    public List<Map<String, JsonElement>> formatJsonList() {
        // gson int format double
        Gson gson = new GsonBuilder().setDateFormat(JsonUtils.DEFAULT_DATE_PATTERN)
                .registerTypeAdapter(ArrayList.class, new JsonDeserializer<List<Map<String, JsonElement>>>() {
                    @Override
                    public List<Map<String, JsonElement>> deserialize(JsonElement json, Type typeof,
                                                                      JsonDeserializationContext context) throws JsonParseException {

                        List<Map<String, JsonElement>> listMaps = new ArrayList<>();
                        Map<String, JsonElement> resultMap = new HashMap<>();

                        if (json.isJsonObject()) {
                            throw new IllegalStateException("Json对象是一个Object，请使用formatJson()");
                        } else if (json.isJsonArray()) {// 纯数组情况
                            JsonArray array = json.getAsJsonArray();
                            int size = array.size();
                            for (int i = 0; i < size; i++) {
                                JsonObject jsonObject = array.get(i).getAsJsonObject();
                                Set<Map.Entry<String, JsonElement>> entrySet = jsonObject.entrySet();
                                for (Map.Entry<String, JsonElement> entry : entrySet) {
                                    resultMap.put(entry.getKey(), entry.getValue());
                                }

                                listMaps.add(resultMap);
                            }
                        }

                        return listMaps;
                    }
                }).create();

        return gson.fromJson(
                asString(),
                new TypeToken<List<Map<String, JsonElement>>>() {
                }.getType());
    }

    public CloseableHttpResponse getResponse() {
        return httpResponse;
    }
}
