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
public class InstantMillisTypeAdapter extends TypeAdapter<Instant> {
    @Override
    public void write(JsonWriter out, Instant value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.toEpochMilli());
        }
    }

    @Override
    public Instant read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        long millis = in.nextLong();
        try {
            return Instant.ofEpochMilli(millis);
        }catch (DateTimeParseException e){
            throw new JsonParseException("Cannot parse Instant of Millis: "+ millis, e);
        }
    }
}
