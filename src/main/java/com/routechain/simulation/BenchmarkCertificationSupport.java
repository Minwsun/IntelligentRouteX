package com.routechain.simulation;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared helpers for certification runners and summaries.
 */
public final class BenchmarkCertificationSupport {
    private static final List<String> BENCHMARK_AUTHORITY_PREFIXES = List.of(
            "build.gradle.kts",
            "src/main/java/com/routechain/simulation/",
            "src/main/java/com/routechain/ai/",
            "src/main/java/com/routechain/infra/PlatformRuntimeBootstrap.java"
    );
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

    @FunctionalInterface
    interface GitCommandExecutor {
        GitCommandResult execute(List<String> command) throws Exception;
    }

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

    public static BenchmarkAuthoritySnapshot collectAuthoritySnapshot(String laneName) {
        return collectAuthoritySnapshot(laneName, BenchmarkCertificationSupport::runGitCommand);
    }

    static BenchmarkAuthoritySnapshot collectAuthoritySnapshot(String laneName, GitCommandExecutor executor) {
        DirtyTrackedPathsProbe dirtyProbe = resolveDirtyTrackedPaths(executor);
        List<String> dirtyTrackedPaths = dirtyProbe.dirtyTrackedPaths();
        List<String> dirtyAuthorityPaths = dirtyTrackedPaths.stream()
                .filter(BenchmarkCertificationSupport::isAuthoritySensitivePath)
                .toList();
        List<String> notes = new ArrayList<>();
        if (dirtyProbe.detectionFailed()) {
            notes.add("benchmark authority detection failed: git status could not be evaluated");
        } else if (dirtyTrackedPaths.isEmpty()) {
            notes.add("tracked worktree is clean for benchmark-sensitive files");
        } else {
            notes.add("tracked worktree has " + dirtyTrackedPaths.size() + " dirty path(s)");
        }
        if (dirtyProbe.detectionFailed()) {
            notes.add("benchmark authority state is unknown: treat this lane as triage-only until git status works");
        } else if (dirtyAuthorityPaths.isEmpty()) {
            notes.add("benchmark authority paths are clean");
        } else {
            notes.add("benchmark authority paths are dirty: summary/verdict should be read as workspace-sensitive");
        }
        if (dirtyProbe.failureDetail() != null && !dirtyProbe.failureDetail().isBlank()) {
            notes.add("authority detection detail: " + dirtyProbe.failureDetail());
        }
        return new BenchmarkAuthoritySnapshot(
                BenchmarkSchema.VERSION,
                laneName,
                Instant.now(),
                resolveGitRevision(),
                !dirtyTrackedPaths.isEmpty(),
                !dirtyAuthorityPaths.isEmpty(),
                dirtyProbe.detectionFailed(),
                dirtyTrackedPaths,
                dirtyAuthorityPaths,
                notes
        );
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

    private static DirtyTrackedPathsProbe resolveDirtyTrackedPaths(GitCommandExecutor executor) {
        try {
            GitCommandResult result = executor.execute(List.of("git", "status", "--short", "--untracked-files=no"));
            if (!result.success()) {
                return new DirtyTrackedPathsProbe(List.of(), true, result.detail());
            }
            String value = result.output();
            List<String> dirtyPaths = new ArrayList<>();
            for (String line : value.split("\\R")) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                String normalized = line.replace('\t', ' ').replace('\\', '/');
                if (normalized.length() < 4) {
                    continue;
                }
                String path = normalized.substring(3).trim();
                if (path.isEmpty()) {
                    continue;
                }
                dirtyPaths.add(path);
            }
            return new DirtyTrackedPathsProbe(dirtyPaths, false, "");
        } catch (Exception e) {
            return new DirtyTrackedPathsProbe(List.of(), true, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static boolean isAuthoritySensitivePath(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        for (String prefix : BENCHMARK_AUTHORITY_PREFIXES) {
            if (normalized.equals(prefix) || normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static GitCommandResult runGitCommand(List<String> command) throws Exception {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        byte[] output = process.getInputStream().readAllBytes();
        int code = process.waitFor();
        return new GitCommandResult(code, new String(output, StandardCharsets.UTF_8).trim());
    }

    record GitCommandResult(int exitCode, String output) {
        boolean success() {
            return exitCode == 0;
        }

        String detail() {
            if (success()) {
                return "";
            }
            if (output == null || output.isBlank()) {
                return "git command exited with code " + exitCode;
            }
            return "git command exited with code " + exitCode + ": " + output;
        }
    }

    record DirtyTrackedPathsProbe(
            List<String> dirtyTrackedPaths,
            boolean detectionFailed,
            String failureDetail
    ) {
        DirtyTrackedPathsProbe {
            dirtyTrackedPaths = dirtyTrackedPaths == null ? List.of() : List.copyOf(dirtyTrackedPaths);
            failureDetail = failureDetail == null ? "" : failureDetail;
        }
    }
}
