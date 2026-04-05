package com.routechain.simulation;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Shared helpers for certification runners and summaries.
 */
public final class BenchmarkCertificationSupport {
    private static final List<String> NON_CURRENT_OMEGA_MARKERS = List.of(
            "legacy",
            "omega-no-",
            "omega-small-batch-only",
            "omega-small_batch_only",
            "no_batch_value",
            "no-batch-value",
            "no_positioning_model",
            "no-positioning-model",
            "no_stress_ai_gate",
            "no-stress-ai-gate",
            "ablation",
            "or-tools-shadow"
    );

    private BenchmarkCertificationSupport() {}

    public static boolean matchesScenario(String value, List<String> matchers) {
        if (value == null || value.isBlank() || matchers == null || matchers.isEmpty()) {
            return false;
        }
        String normalized = normalize(value);
        for (String matcher : matchers) {
            if (matcher != null && !matcher.isBlank() && normalized.contains(normalize(matcher))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCurrentOmegaRun(RunReport report) {
        if (report == null) {
            return false;
        }
        return isCurrentOmegaDescriptor(report.scenarioName() + " " + report.runId());
    }

    public static boolean isCurrentOmegaCompare(ReplayCompareResult compare) {
        if (compare == null) {
            return false;
        }
        String left = compare.scenarioA() + " " + compare.runIdA();
        String right = compare.scenarioB() + " " + compare.runIdB();
        return isLegacyDescriptor(left) && isCurrentOmegaDescriptor(right);
    }

    public static boolean isLegacyDescriptor(String descriptor) {
        return normalize(descriptor).contains("legacy");
    }

    public static boolean isCurrentOmegaDescriptor(String descriptor) {
        String normalized = normalize(descriptor);
        if (normalized.isBlank()) {
            return false;
        }
        for (String marker : NON_CURRENT_OMEGA_MARKERS) {
            if (normalized.contains(marker)) {
                return false;
            }
        }
        return normalized.contains("omega")
                || normalized.contains("instant-")
                || normalized.contains("normal-")
                || normalized.contains("rush_hour-")
                || normalized.contains("demand_spike-")
                || normalized.contains("heavy_rain-")
                || normalized.contains("shortage-")
                || normalized.contains("post_drop_shortage")
                || normalized.contains("merchant_cluster");
    }

    public static String resolveGitRevision() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                    .redirectErrorStream(true)
                    .start();
            byte[] output = process.getInputStream().readAllBytes();
            int code = process.waitFor();
            if (code != 0) {
                return "unknown";
            }
            String value = new String(output, StandardCharsets.UTF_8).trim();
            return value.isBlank() ? "unknown" : value;
        } catch (Exception e) {
            return "unknown";
        }
    }

    public static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public static double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }
}
