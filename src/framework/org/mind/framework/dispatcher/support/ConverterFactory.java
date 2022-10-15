package org.mind.framework.dispatcher.support;

import org.apache.commons.lang3.CharUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Factory for all converters.
 *
 * @author dp
 */
public class ConverterFactory {

    private final Map<Class<?>, Converter<?>> mapHolder;

    private ConverterFactory() {
        Converter<Boolean> booleanConvert = value -> Boolean.parseBoolean(value);

        Converter<Long> longConvert = value -> Long.parseLong(value);

        Converter<Integer> intConvert = value -> Integer.parseInt(value);

        Converter<Float> floatConvert = value -> Float.parseFloat(value);

        Converter<Double> doubleConvert = value -> Double.parseDouble(value);

        Converter<Short> shortConvert = value -> Short.parseShort(value);

        Converter<Byte> byteConvert = value -> Byte.parseByte(value);

        Converter<Character> charConvert = value -> CharUtils.toChar(value);

        this.mapHolder = new HashMap<>(16);
        this.mapHolder.put(boolean.class, booleanConvert);
        this.mapHolder.put(Boolean.class, booleanConvert);

        this.mapHolder.put(int.class, intConvert);
        this.mapHolder.put(Integer.class, intConvert);

        this.mapHolder.put(long.class, longConvert);
        this.mapHolder.put(Long.class, longConvert);

        this.mapHolder.put(short.class, shortConvert);
        this.mapHolder.put(Short.class, shortConvert);

        this.mapHolder.put(byte.class, byteConvert);
        this.mapHolder.put(Byte.class, byteConvert);

        this.mapHolder.put(float.class, floatConvert);
        this.mapHolder.put(Float.class, floatConvert);

        this.mapHolder.put(double.class, doubleConvert);
        this.mapHolder.put(Double.class, doubleConvert);

        this.mapHolder.put(char.class, charConvert);
        this.mapHolder.put(Character.class, charConvert);
    }

    private static class ConverterHolder {
        private static final ConverterFactory factory = new ConverterFactory();
        private ConverterHolder() {
        }
    }

    public static ConverterFactory getInstance() {
        return ConverterHolder.factory;
    }

    public boolean isConvert(Class<?> clazz) {
        return
                String.class.equals(clazz)
                        || this.mapHolder.get(clazz) != null;
    }

    public Object convert(Class<?> clazz, String value) {
        Converter<?> convert = this.mapHolder.get(clazz);
        Objects.requireNonNull(convert, "The parameter conversion type failed, Only supports basic data types.");
        return convert.convert(value);
    }
}
