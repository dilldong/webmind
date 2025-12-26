package org.mind.framework.util;

import lombok.Getter;
import lombok.ToString;

/**
 * @version 1.0
 * @author Marcus
 * @date 2023/7/21
 */
@Getter
@ToString
public class WeightedNode<T> {
    private final T value;
    private final int weight;

    private WeightedNode(T value, int weight) {
        this.value = value;
        this.weight = weight;
    }

    public static <T> WeightedNode<T> newNode(T value) {
        return new WeightedNode<>(value, 1);
    }

    public static <T> WeightedNode<T> newNode(T value, int weight) {
        return new WeightedNode<>(value, weight);
    }
}
