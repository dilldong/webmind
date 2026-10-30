package org.mind.framework.annotation.processor;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.mind.framework.annotation.Cachein;
import org.mind.framework.annotation.CacheinFace;
import org.mind.framework.annotation.Cacheout;
import org.mind.framework.exception.NotSupportedException;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.web.dispatcher.support.ConverterFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 *
 * @author Marcus
 * @version 1.0
 * @date 2026/10/25
 */
public class CacheKeyResolver {
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    public static String resolveStaticKey(Cachein cachein) {
        return resolveStaticKey(cachein.delimiter(), cachein.prefix(), cachein.suffix());
    }

    public static String resolveStaticKey(Cacheout cacheout) {
        return resolveStaticKey(cacheout.delimiter(), cacheout.prefix(), cacheout.suffix());
    }

    public static String resolveStaticKey(String delimiter, String prefix, String suffix){
        boolean isPrefix = StringUtils.isNotEmpty(prefix);
        boolean isSuffix = StringUtils.isNotEmpty(suffix);

        if (isPrefix && isSuffix)
            return String.join(delimiter, prefix, suffix);
        else if (isPrefix)
            return prefix;
        else if (isSuffix)
            return suffix;
        else
            throw new IllegalArgumentException("At least one of 'prefix' or 'suffix' must be specified");
    }

    public static String resolverExpl(String staticKey, Object[] params, Method method, String delimiter) {
        if (ArrayUtils.isEmpty(params))
            return staticKey;

        if (MatcherUtils.checkCount(staticKey, MatcherUtils.PARAM_MATCH_PATTERN) == 0)
            return staticKey;

        /*
         * 使用 Spring 提供的方法解析 + 参数名发现器
         * 接口 → 实现类
         * bridge method
         * 泛型擦除问题
         */
        String[] paramNames = PARAMETER_NAME_DISCOVERER.getParameterNames(method);

        Objects.requireNonNull(paramNames);
        int size = params.length;
        String resolveKey = staticKey;

        for (int i = 0; i < size; ++i) {
            String value = null;
            if (Objects.nonNull(params[i])) {
                if (ConverterFactory.getInstance().isConvert(params[i].getClass()))
                    value = String.valueOf(params[i]);
                else if (params[i] instanceof CacheinFace<?> v)
                    value = String.valueOf(v.getValue());
                else if (params[i].getClass().isArray())
                    value = arrayToString(params[i], delimiter);
                else if (params[i] instanceof Collection<?> collection) {
                    value = collection.stream().map(v -> {
                        if (v instanceof CacheinFace<?> face)
                            return String.valueOf(face.getValue());
                        return String.valueOf(v);
                    }).collect(Collectors.joining(delimiter));
                } else {
                    throw new NotSupportedException("Key value conversion failed. Supported types: basic-types, one-dimensional arrays(basic-types), CacheinFace, Collection(basic-types and CacheinFace");
                }
            }

            resolveKey = Strings.CS.replace(resolveKey, "#{" + paramNames[i] + "}", value);
        }

        return resolveKey;
    }

    private static String arrayToString(Object obj, String delimiter) {
        int length = ArrayUtils.getLength(obj);
        if (length == 0)
            return StringUtils.EMPTY;

        // If a basic type array
        if (obj.getClass().getComponentType().isPrimitive()) {
            return IntStream.range(0, length)
                    .mapToObj(i -> String.valueOf(Array.get(obj, i)))
                    .collect(Collectors.joining(delimiter));
        }

        // Object type array
        return Stream.of((Object[]) obj).map(v -> {
            if (v instanceof CacheinFace<?> face)
                return String.valueOf(face.getValue());
            return String.valueOf(v);
        }).collect(Collectors.joining(delimiter));
    }
}
