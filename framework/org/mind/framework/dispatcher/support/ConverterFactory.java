package org.mind.framework.dispatcher.support;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for all converters.
 * 
 * @author dp
 */
public class ConverterFactory {
	
    private Converter<Boolean> booleanConvert;
    private Converter<Long> longConvert;
    private Converter<Integer> intConvert;
    private Converter<Float> floatConvert;
    private Converter<Double> doubleConvert;
    private Converter<Short> shortConvert;
    private Converter<Byte> byteConvert;
    
    private Map<Class<?>, Converter<?>> mapHolder;

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
		
		this.mapHolder = new HashMap<Class<?>, Converter<?>>(14);
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
    }
    
    public static class ConverterHolder{
    	private static ConverterFactory factory = new ConverterFactory();
    }
    
    public static ConverterFactory getInstance(){
    	return ConverterHolder.factory;
    }

    public boolean isConvert(Class<?> clazz) {
        return 
        		String.class.equals(clazz)
        		|| this.mapHolder.get(clazz) != null;
    }

    public Object convert(Class<?> clazz, String value) {
    	Converter<?> convert = this.mapHolder.get(clazz);
        return convert.convert(value);
    }
}
