package pl.yourserver.bloodChestPlugin.util;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToDoubleFunction;

public final class WeightedPicker {

    private WeightedPicker() {
    }

    public static <T> T pick(List<T> elements, ToDoubleFunction<T> weightFunction) {
        if (elements.isEmpty()) {
            return null;
        }
        double totalWeight = 0.0D;
        for (T element : elements) {
            totalWeight += Math.max(0.0D, weightFunction.applyAsDouble(element));
        }
        if (totalWeight <= 0.0D) {
            return elements.get(ThreadLocalRandom.current().nextInt(elements.size()));
        }
        double randomValue = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0.0D;
        for (T element : elements) {
            cumulative += Math.max(0.0D, weightFunction.applyAsDouble(element));
            if (randomValue <= cumulative) {
                return element;
            }
        }
        return elements.get(elements.size() - 1);
    }

    public static <T> T pick(Collection<T> elements, ToDoubleFunction<T> weightFunction) {
        return pick(List.copyOf(elements), weightFunction);
    }
}
