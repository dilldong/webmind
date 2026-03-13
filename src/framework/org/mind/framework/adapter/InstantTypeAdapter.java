package org.mind.framework.adapter;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Gson 对 JSR-310 时间类型支持
 * @author: Marcus
 * @date: 2026/3/11
 * @version: 1.0
 */
public class InstantTypeAdapter extends TypeAdapter<Instant> {
    @Override
    public void write(JsonWriter out, Instant value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            // Instant.toString() 默认 ISO-8601 UTC 格式，例如：2025-10-27T08:00:00Z
            out.value(value.toString());
        }
    }

    @Override
    public Instant read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        String dateStr = in.nextString();
        try {
            return Instant.parse(dateStr);
        }catch (DateTimeParseException e){
            throw new JsonParseException("Cannot parse Instant: " + dateStr, e);
        }
    }
}
