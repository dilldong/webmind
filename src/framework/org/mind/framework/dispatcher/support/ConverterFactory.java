package org.mind.framework.dispatcher.support;

import org.apache.commons.lang3.CharUtils;
import org.mind.framework.exception.NotSupportedException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Factory for all converters.
 *
 * @author dp
 */
public class ConverterFactory {

    private final Map<String, Converter<?>> converterMap;

    private ConverterFactory() {
        Converter<Boolean> booleanConvert = value -> Boolean.parseBoolean(value);

        Converter<Long> longConvert = value -> Long.parseLong(value);

        Converter<Integer> intConvert = value -> Integer.parseInt(value);

        Converter<BigInteger> bigIntConvert = value -> new BigInteger(value);

        Converter<BigDecimal> bigDecimalConvert = value -> new BigDecimal(value);

        Converter<Float> floatConvert = value -> Float.parseFloat(value);

        Converter<Double> doubleConvert = value -> Double.parseDouble(value);

        Converter<Short> shortConvert = value -> Short.parseShort(value);

        Converter<Byte> byteConvert = value -> Byte.parseByte(value);

        Converter<Character> charConvert = value -> CharUtils.toChar(value);

        this.converterMap = new HashMap<>(16);
        this.converterMap.put(boolean.class.getName(), booleanConvert);
        this.converterMap.put(Boolean.class.getName(), booleanConvert);

        this.converterMap.put(int.class.getName(), intConvert);
        this.converterMap.put(Integer.class.getName(), intConvert);

        this.converterMap.put(long.class.getName(), longConvert);
        this.converterMap.put(Long.class.getName(), longConvert);

        this.converterMap.put(short.class.getName(), shortConvert);
        this.converterMap.put(Short.class.getName(), shortConvert);

        this.converterMap.put(byte.class.getName(), byteConvert);
        this.converterMap.put(Byte.class.getName(), byteConvert);

        this.converterMap.put(float.class.getName(), floatConvert);
        this.converterMap.put(Float.class.getName(), floatConvert);

        this.converterMap.put(double.class.getName(), doubleConvert);
        this.converterMap.put(Double.class.getName(), doubleConvert);

        this.converterMap.put(char.class.getName(), charConvert);
        this.converterMap.put(Character.class.getName(), charConvert);

        this.converterMap.put(BigInteger.class.getName(), bigIntConvert);
        this.converterMap.put(BigDecimal.class.getName(), bigDecimalConvert);
    }

    private static class ConverterHolder {
        private static final ConverterFactory CONVERTER_FACTORY = new ConverterFactory();
        private ConverterHolder() {
        }
    }

    public static ConverterFactory getInstance() {
        return ConverterHolder.CONVERTER_FACTORY;
    }

    public boolean isConvert(Class<?> clazz) {
        return
                String.class.getName().equals(clazz.getName())
                        || this.converterMap.containsKey(clazz.getName());
    }

    public Object convert(Class<?> clazz, String value) {
        if (this.converterMap.containsKey(clazz.getName())) {
            Converter<?> convert = this.converterMap.get(clazz.getName());
            if (Objects.isNull(convert))
                throw new NotSupportedException("The parameter conversion type failed, Only supports basic data types.");
            return convert.convert(value);
        }
        throw new NotSupportedException("The parameter conversion type failed, Only supports basic data types.");
    }
}
