package org.mind.framework.web.dispatcher.support;

import org.apache.commons.lang3.BooleanUtils;
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
        Converter<Boolean> booleanConvert = BooleanUtils::toBoolean;

        Converter<Long> longConvert = Long::parseLong;

        Converter<Integer> intConvert = Integer::parseInt;

        Converter<BigInteger> bigIntConvert = BigInteger::new;

        Converter<BigDecimal> bigDecimalConvert = BigDecimal::new;

        Converter<Float> floatConvert = Float::parseFloat;

        Converter<Double> doubleConvert = Double::parseDouble;

        Converter<Short> shortConvert = Short::parseShort;

        Converter<Byte> byteConvert = Byte::parseByte;

        Converter<Character> charConvert = CharUtils::toChar;

        this.converterMap = new HashMap<>(18);
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
    }

    public static ConverterFactory getInstance() {
        return ConverterHolder.CONVERTER_FACTORY;
    }

    public boolean isConvert(Class<?> clazz) {
        return
                String.class.getName().equals(clazz.getName())
                        || this.converterMap.containsKey(clazz.getName());
    }

    public <T> T convert(Class<T> clazz, String value) {
        if (this.converterMap.containsKey(clazz.getName())) {
            Converter<?> convert = this.converterMap.get(clazz.getName());
            if (Objects.nonNull(convert))
                return (T)convert.convert(value);
        }
        throw new NotSupportedException("The parameter conversion type failed, Only supports basic data types.");
    }
}
