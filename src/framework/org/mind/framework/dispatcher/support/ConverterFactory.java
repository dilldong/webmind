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

    private final Converter<Boolean> booleanConvert;
    private final Converter<Long> longConvert;
    private final Converter<Integer> intConvert;
    private final Converter<Float> floatConvert;
    private final Converter<Double> doubleConvert;
    private final Converter<Short> shortConvert;
    private final Converter<Byte> byteConvert;
    private final Converter<Character> charConvert;

    private final Map<Class<?>, Converter<?>> mapHolder;

    private ConverterFactory() {

        this.booleanConvert = new Converter<Boolean>() {
            @Override
            public Boolean convert(String value) {
                return Boolean.parseBoolean(value);
            }
        };

        this.longConvert = new Converter<Long>() {
            @Override
            public Long convert(String value) {
                return Long.parseLong(value);
            }
        };

        this.intConvert = new Converter<Integer>() {
            @Override
            public Integer convert(String value) {
                return Integer.parseInt(value);
            }
        };

        this.floatConvert = new Converter<Float>() {
            @Override
            public Float convert(String value) {
                return Float.parseFloat(value);
            }
        };

        this.doubleConvert = new Converter<Double>() {
            @Override
            public Double convert(String value) {
                return Double.parseDouble(value);
            }
        };

        this.shortConvert = new Converter<Short>() {
            @Override
            public Short convert(String value) {
                return Short.parseShort(value);
            }
        };

        this.byteConvert = new Converter<Byte>() {
            @Override
            public Byte convert(String value) {
                return Byte.parseByte(value);
            }
        };

        this.charConvert = new Converter<Character>() {
            @Override
            public Character convert(String value) {
                return CharUtils.toChar(value);
            }
        };

        this.mapHolder = new HashMap<>(16);
        this.mapHolder.put(boolean.class, this.booleanConvert);
        this.mapHolder.put(Boolean.class, this.booleanConvert);

        this.mapHolder.put(int.class, this.intConvert);
        this.mapHolder.put(Integer.class, this.intConvert);

        this.mapHolder.put(long.class, this.longConvert);
        this.mapHolder.put(Long.class, this.longConvert);

        this.mapHolder.put(short.class, this.shortConvert);
        this.mapHolder.put(Short.class, this.shortConvert);

        this.mapHolder.put(byte.class, this.byteConvert);
        this.mapHolder.put(Byte.class, this.byteConvert);

        this.mapHolder.put(float.class, this.floatConvert);
        this.mapHolder.put(Float.class, this.floatConvert);

        this.mapHolder.put(double.class, this.doubleConvert);
        this.mapHolder.put(Double.class, this.doubleConvert);

        this.mapHolder.put(char.class, this.charConvert);
        this.mapHolder.put(Character.class, this.charConvert);
    }

    public static class ConverterHolder {
        private static final ConverterFactory factory = new ConverterFactory();
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
