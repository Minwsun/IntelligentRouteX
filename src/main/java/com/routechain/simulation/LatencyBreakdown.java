package com.routechain.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runtime latency profile split by hot-path decision and business assignment aging.
 */
public record LatencyBreakdown(
        double avgDispatchDecisionLatencyMs,
        double dispatchP50Ms,
        double dispatchP95Ms,
        double dispatchP99Ms,
        double modelP50Ms,
        double modelP95Ms,
        double neuralPriorP50Ms,
        double neuralPriorP95Ms,
        double assignmentAgingP50Ms,
        double assignmentAgingP95Ms,
        double tickThroughputPerSec,
        int dispatchSampleCount,
        int assignmentSampleCount
) {
    public static LatencyBreakdown empty() {
        return new LatencyBreakdown(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0);
    }

    public static LatencyBreakdown fromSamples(List<Long> dispatchSamples,
                                               List<Long> modelSamples,
                                               List<Long> neuralSamples,
                                               List<Long> assignmentAgingSamples,
                                               double tickThroughputPerSec) {
        List<Double> dispatch = toSortedDoubles(dispatchSamples);
        List<Double> model = toSortedDoubles(modelSamples);
        List<Double> neural = toSortedDoubles(neuralSamples);
        List<Double> assignment = toSortedDoubles(assignmentAgingSamples);
        return new LatencyBreakdown(
                mean(dispatch),
                percentile(dispatch, 0.50),
                percentile(dispatch, 0.95),
                percentile(dispatch, 0.99),
                percentile(model, 0.50),
                percentile(model, 0.95),
                percentile(neural, 0.50),
                percentile(neural, 0.95),
                percentile(assignment, 0.50),
                percentile(assignment, 0.95),
                Math.max(0.0, tickThroughputPerSec),
                dispatch.size(),
                assignment.size()
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
