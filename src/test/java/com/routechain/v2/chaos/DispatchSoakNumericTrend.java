package com.routechain.v2.chaos;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DispatchSoakNumericTrend(
        String schemaVersion,
        List<Double> samples,
        double startValue,
        double endValue,
        double minValue,
        double maxValue,
        double averageValue) implements SchemaVersioned {

    public static DispatchSoakNumericTrend fromSamples(List<Double> samples) {
        if (samples == null || samples.isEmpty()) {
            return new DispatchSoakNumericTrend("dispatch-soak-numeric-trend/v1", List.of(), 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        double min = samples.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = samples.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double average = samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return new DispatchSoakNumericTrend(
                "dispatch-soak-numeric-trend/v1",
                List.copyOf(samples),
                samples.getFirst(),
                samples.getLast(),
                min,
                max,
                average);
    }
}
