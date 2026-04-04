package com.routechain.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Percentile summary for one dispatch stage.
 */
public record StageLatencyStat(
        double avgMs,
        double p50Ms,
        double p95Ms,
        double p99Ms,
        double maxMs,
        int sampleCount
) {
    public static StageLatencyStat empty() {
        return new StageLatencyStat(0.0, 0.0, 0.0, 0.0, 0.0, 0);
    }

    public static StageLatencyStat fromSamples(List<Long> samples) {
        List<Double> values = toSortedDoubles(samples);
        if (values.isEmpty()) {
            return empty();
        }
        return new StageLatencyStat(
                mean(values),
                percentile(values, 0.50),
                percentile(values, 0.95),
                percentile(values, 0.99),
                values.get(values.size() - 1),
                values.size()
        );
    }

    private static List<Double> toSortedDoubles(List<Long> samples) {
        if (samples == null || samples.isEmpty()) {
            return List.of();
        }
        List<Double> values = new ArrayList<>(samples.size());
        for (Long sample : samples) {
            if (sample != null && sample >= 0L) {
                values.add(sample.doubleValue());
            }
        }
        Collections.sort(values);
        return values;
    }

    private static double mean(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private static double percentile(List<Double> values, double quantile) {
        if (values.isEmpty()) {
            return 0.0;
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        double rank = quantile * (values.size() - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) {
            return values.get(lower);
        }
        double weight = rank - lower;
        return values.get(lower) * (1.0 - weight) + values.get(upper) * weight;
    }
}
