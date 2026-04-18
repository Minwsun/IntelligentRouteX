package com.routechain.v2.perf;

import com.routechain.v2.SchemaVersioned;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record DispatchPerfNumericStats(
        String schemaVersion,
        long minMs,
        long p50Ms,
        long p95Ms,
        long p99Ms,
        long maxMs,
        double averageMs) implements SchemaVersioned {

    public static DispatchPerfNumericStats empty() {
        return new DispatchPerfNumericStats("dispatch-perf-numeric-stats/v1", 0L, 0L, 0L, 0L, 0L, 0.0);
    }

    public static DispatchPerfNumericStats fromSamples(List<Long> samples) {
        if (samples == null || samples.isEmpty()) {
            return empty();
        }
        List<Long> sorted = new ArrayList<>(samples);
        sorted.sort(Comparator.naturalOrder());
        long min = sorted.getFirst();
        long max = sorted.getLast();
        double average = sorted.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
        return new DispatchPerfNumericStats(
                "dispatch-perf-numeric-stats/v1",
                min,
                percentile(sorted, 0.50),
                percentile(sorted, 0.95),
                percentile(sorted, 0.99),
                max,
                average);
    }

    private static long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        int boundedIndex = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(boundedIndex);
    }
}
