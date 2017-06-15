package org.mind.framework.dispatcher.support;

/**
 * Convert String to any given type.
 *
 * @param <T> Generic type of converted result.
 * @author dp
 */
public interface Converter<T> {

    /**
     * Convert a not-null String to specified object.
     */
    T convert(String value);

}
