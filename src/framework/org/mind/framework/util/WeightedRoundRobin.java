package org.mind.framework.util;

import java.util.List;

/**
 * 加权轮询
 *
 * @version 1.0
 * @author Marcus
 * @date 2023/7/21
 */
public class WeightedRoundRobin<T> {
    private volatile int currentIndex;
    private volatile int currentWeight;

    private final List<WeightedNode<T>> nodes;
    private final int maxWeight;
    private final int gcdWeight;

    public WeightedRoundRobin(List<WeightedNode<T>> nodes) {
        this.nodes = nodes;
        currentIndex = -1;
        currentWeight = 0;
        maxWeight = getMaxWeight();
        gcdWeight = getGcdWeight();
    }

    public WeightedNode<T> getNext() {
        synchronized (WeightedRoundRobin.class) {
            while (true) {
                currentIndex = (currentIndex + 1) % nodes.size();
                if (currentIndex == 0) {
                    currentWeight = currentWeight - gcdWeight;
                    if (currentWeight < 1) {
                        currentWeight = maxWeight;
                        if (currentWeight == 0)
                            return null;
                    }
                }

                if (nodes.get(currentIndex).getWeight() >= currentWeight)
                    return nodes.get(currentIndex);
            }
        }
    }

    private int getMaxWeight() {
        int maxWeight = 0;
        for (WeightedNode<T> node : nodes)
            maxWeight = Math.max(maxWeight, node.getWeight());

        return maxWeight;
    }

    private int getGcdWeight() {
        int gcdWeight = nodes.get(0).getWeight();
        for (WeightedNode<T> node : nodes)
            gcdWeight = gcd(gcdWeight, node.getWeight());

        return gcdWeight;
    }

    /**
     * 计算两个数的最大公约数
     * 使用最大公约数来选择节点，可以避免因为节点权重的不同而导致跳跃式的切换，从而使加权轮询算法更加平滑地选择节点。
     * 这样可以确保所有节点被充分利用，并且在节点权重发生变化时，也可以根据最大公约数进行调整，保持平滑的轮询效果。
     */
    private int gcd(int a, int b) {
        while (b != 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }
}


