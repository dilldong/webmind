package org.mind.framework.web.dispatcher.support;

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
