package com.routechain.core;

import java.util.EnumMap;
import java.util.Map;

public record WeightSnapshot(
        Map<RegimeKey, double[]> weights,
        Map<RegimeKey, Double> confidences,
        Map<RegimeKey, Double> learningRates,
        Map<RegimeKey, Integer> sampleCounts,
        Map<String, Double> dualPenalties) {

    public static WeightSnapshot copyOf(Map<RegimeKey, double[]> weights,
                                        Map<RegimeKey, Double> confidences,
                                        Map<RegimeKey, Double> learningRates,
                                        Map<RegimeKey, Integer> sampleCounts,
                                        Map<String, Double> dualPenalties) {
        Map<RegimeKey, double[]> weightCopy = new EnumMap<>(RegimeKey.class);
        for (Map.Entry<RegimeKey, double[]> entry : weights.entrySet()) {
            weightCopy.put(entry.getKey(), entry.getValue().clone());
        }
        return new WeightSnapshot(
                weightCopy,
                new EnumMap<>(confidences),
                new EnumMap<>(learningRates),
                new EnumMap<>(sampleCounts),
                Map.copyOf(dualPenalties));
    }
}
