package com.routechain.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lightweight statistical utilities used by benchmark harnesses.
 */
public final class BenchmarkStatistics {

    private BenchmarkStatistics() {}

    public static BenchmarkStatSummary summarize(String metricName,
                                                 String scope,
                                                 List<Double> values) {
        List<Double> clean = sanitize(values);
        if (clean.isEmpty()) {
            return new BenchmarkStatSummary(
                    BenchmarkSchema.VERSION,
                    metricName,
                    metricClassFor(metricName),
                    scope,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    null,
                    null,
                    null
            );
        }
        double mean = mean(clean);
        double stdDev = stdDev(clean, mean);
        double stderr = stdDev / Math.sqrt(clean.size());
        double margin95 = 1.96 * stderr;
        return new BenchmarkStatSummary(
                BenchmarkSchema.VERSION,
                metricName,
                metricClassFor(metricName),
                scope,
                clean.size(),
                mean,
                percentile(clean, 0.50),
                percentile(clean, 0.95),
                stdDev,
                mean - margin95,
                mean + margin95,
                null,
                null,
                null
        );
    }

    public static BenchmarkStatSummary summarizeComparison(String metricName,
                                                           String scope,
                                                           List<Double> baselineValues,
                                                           List<Double> candidateValues) {
        List<Double> baseline = sanitize(baselineValues);
        List<Double> candidate = sanitize(candidateValues);
        if (baseline.isEmpty() || candidate.isEmpty()) {
            return summarize(metricName, scope, List.of());
        }
        BenchmarkStatSummary baseSummary = summarize(metricName, scope + "-baseline", baseline);
        BenchmarkStatSummary candidateSummary = summarize(metricName, scope + "-candidate", candidate);
        double effectSize = cohensD(baseline, candidate);
        double pValue = welchTwoSidedPValue(baseline, candidate);
        boolean significant = pValue < 0.05;
        return new BenchmarkStatSummary(
                BenchmarkSchema.VERSION,
                metricName,
                metricClassFor(metricName),
                scope,
                Math.min(baseline.size(), candidate.size()),
                candidateSummary.mean() - baseSummary.mean(),
                candidateSummary.median() - baseSummary.median(),
                candidateSummary.p95() - baseSummary.p95(),
                candidateSummary.stdDev(),
                candidateSummary.ci95Low() - baseSummary.ci95High(),
                candidateSummary.ci95High() - baseSummary.ci95Low(),
                effectSize,
                pValue,
                significant
        );
    }

    private static List<Double> sanitize(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Double> clean = new ArrayList<>(values.size());
        for (Double value : values) {
            if (value == null) {
                continue;
            }
            if (Double.isFinite(value)) {
                clean.add(value);
            }
        }
        return clean;
    }

    private static double mean(List<Double> values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private static double stdDev(List<Double> values, double mean) {
        if (values.size() <= 1) {
            return 0.0;
        }
        double sumSq = 0.0;
        for (double value : values) {
            double diff = value - mean;
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq / (values.size() - 1));
    }

    private static double percentile(List<Double> values, double q) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        double rank = q * (sorted.size() - 1);
        int low = (int) Math.floor(rank);
        int high = (int) Math.ceil(rank);
        if (low == high) {
            return sorted.get(low);
        }
        double weight = rank - low;
        return sorted.get(low) * (1.0 - weight) + sorted.get(high) * weight;
    }

    private static double cohensD(List<Double> baseline, List<Double> candidate) {
        double m1 = mean(baseline);
        double m2 = mean(candidate);
        double sd1 = stdDev(baseline, m1);
        double sd2 = stdDev(candidate, m2);
        double pooledVarNumerator = ((baseline.size() - 1) * sd1 * sd1)
                + ((candidate.size() - 1) * sd2 * sd2);
        int pooledVarDenominator = (baseline.size() + candidate.size() - 2);
        if (pooledVarDenominator <= 0) {
            return 0.0;
        }
        double pooledStdDev = Math.sqrt(Math.max(1e-12, pooledVarNumerator / pooledVarDenominator));
        return (m2 - m1) / pooledStdDev;
    }

    private static double welchTwoSidedPValue(List<Double> baseline, List<Double> candidate) {
        double m1 = mean(baseline);
        double m2 = mean(candidate);
        double s1 = stdDev(baseline, m1);
        double s2 = stdDev(candidate, m2);
        double n1 = baseline.size();
        double n2 = candidate.size();
        if (n1 < 2 || n2 < 2) {
            return 1.0;
        }
        double seSquared = (s1 * s1 / n1) + (s2 * s2 / n2);
        if (seSquared <= 1e-12) {
            return 1.0;
        }
        double t = Math.abs((m2 - m1) / Math.sqrt(seSquared));
        // Normal approximation is stable enough for benchmark gating and avoids extra deps.
        double cdf = normalCdf(t);
        return Math.max(0.0, Math.min(1.0, 2.0 * (1.0 - cdf)));
    }

    private static double normalCdf(double x) {
        // Abramowitz-Stegun approximation
        double sign = x < 0 ? -1.0 : 1.0;
        double absX = Math.abs(x) / Math.sqrt(2.0);
        double t = 1.0 / (1.0 + 0.3275911 * absX);
        double erf = 1.0 - (((((1.061405429 * t - 1.453152027) * t)
                + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-absX * absX);
        return 0.5 * (1.0 + sign * erf);
    }

    private static String metricClassFor(String metricName) {
        if (metricName == null || metricName.isBlank()) {
            return "business";
        }
        String normalized = metricName.toLowerCase();
        if (normalized.contains("latency") || normalized.contains("throughput") || normalized.contains("memory")
                || normalized.contains("gc")) {
            return "runtime";
        }
        if (normalized.contains("forecast") || normalized.contains("calibration") || normalized.contains("mae")) {
            return "forecast";
        }
        if (normalized.contains("coverage") || normalized.contains("borrow") || normalized.contains("no_driver")
                || normalized.contains("reserve") || normalized.contains("network")) {
            return "network";
        }
        return "business";
    }
}
