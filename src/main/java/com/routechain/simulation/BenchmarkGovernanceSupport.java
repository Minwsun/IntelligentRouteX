package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.infra.ArtifactPaths;
import com.routechain.infra.GsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared helpers for the benchmark-governed experimentation spine.
 */
public final class BenchmarkGovernanceSupport {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path GOVERNANCE_DIR = ArtifactPaths.benchmarksRoot().resolve("governance");
    private static final Path CURRENT_BASELINE_JSON = GOVERNANCE_DIR.resolve("benchmark-baseline-current.json");
    private static final Path CERTIFICATION_DIR = ArtifactPaths.benchmarksRoot().resolve("certification");

    private BenchmarkGovernanceSupport() {}

    public static String authorityStatus(BenchmarkAuthoritySnapshot snapshot) {
        if (snapshot == null) {
            return "UNKNOWN";
        }
        if (snapshot.authorityDetectionFailed()) {
            return "AUTHORITY_CHECK_FAILED";
        }
        if (snapshot.authorityDirty()) {
            return "DIRTY_TRIAGE_ONLY";
        }
        return "CLEAN_CANONICAL_CHECKPOINT";
    }

    public static boolean promotionEligible(BenchmarkCheckpointSummary summary) {
        return summary != null
                && "CLEAN_CANONICAL_CHECKPOINT".equals(summary.checkpointStatus())
                && !summary.degradedCheckpoint()
                && summary.promotionEligible();
    }

    public static BenchmarkBaselineRef loadCurrentBaseline() {
        return readOptionalJson(CURRENT_BASELINE_JSON, BenchmarkBaselineRef.class);
    }

    public static BenchmarkCheckpointSummary loadCheckpointSummary(String laneName) {
        Path path = CERTIFICATION_DIR.resolve("benchmark-checkpoint-" + laneName + ".json");
        return readOptionalJson(path, BenchmarkCheckpointSummary.class);
    }

    public static BenchmarkBaselineRef resolveBaselineForLane(String laneName) {
        BenchmarkBaselineRef current = loadCurrentBaseline();
        if (current != null) {
            return current;
        }
        BenchmarkCheckpointSummary checkpoint = loadCheckpointSummary(laneName);
        if (!promotionEligible(checkpoint)) {
            return null;
        }
        return new BenchmarkBaselineRef(
                BenchmarkSchema.VERSION,
                "baseline-bootstrap-" + checkpoint.checkpointId(),
                checkpoint.checkpointId(),
                checkpoint.laneName(),
                Instant.now(),
                checkpoint.gitRevision(),
                checkpoint.checkpointStatus(),
                "bootstrap-no-decision",
                "",
                true,
                List.of("bootstrap baseline resolved directly from clean checkpoint because no baseline registry entry existed")
        );
    }

    public static BenchmarkAuthoritySnapshot loadAuthoritySnapshot(Path artifactRoot, String laneName) {
        Path path = artifactRoot.resolve("benchmarks").resolve("certification")
                .resolve("benchmark-authority-" + laneName + ".json");
        return readOptionalJson(path, BenchmarkAuthoritySnapshot.class);
    }

    public static BenchmarkCheckpointSummary loadCheckpointSummary(Path artifactRoot, String laneName) {
        Path path = artifactRoot.resolve("benchmarks").resolve("certification")
                .resolve("benchmark-checkpoint-" + laneName + ".json");
        return readOptionalJson(path, BenchmarkCheckpointSummary.class);
    }

    public static RouteQualityBlockerSummary loadBlockerSummary(Path artifactRoot, String laneName) {
        Path path = artifactRoot.resolve("benchmarks").resolve("certification")
                .resolve("route-quality-blockers-" + laneName + ".json");
        return readOptionalJson(path, RouteQualityBlockerSummary.class);
    }

    public static Path governanceDir() {
        return GOVERNANCE_DIR;
    }

    public static String checkpointId(String laneName, String gitRevision, Instant generatedAt) {
        String safeLane = laneName == null || laneName.isBlank() ? "unknown" : laneName.trim().toLowerCase(Locale.ROOT);
        String safeGit = gitRevision == null || gitRevision.isBlank() ? "unknown" : gitRevision.trim();
        Instant timestamp = generatedAt == null ? Instant.now() : generatedAt;
        return safeLane + "-" + safeGit + "-" + timestamp.toEpochMilli();
    }

    public static List<String> defaultPhase31Buckets() {
        return List.of("HEAVY_RAIN", "NIGHT_OFF_PEAK");
    }

    public static CommandResult runCommand(List<String> command, Path workingDirectory) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            return new CommandResult(exitCode, output == null ? "" : output.trim());
        } catch (Exception e) {
            return new CommandResult(1, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public static List<String> normalizeBuckets(String bucketCsv) {
        if (bucketCsv == null || bucketCsv.isBlank()) {
            return defaultPhase31Buckets();
        }
        List<String> buckets = new ArrayList<>();
        for (String raw : bucketCsv.split(",")) {
            String value = raw == null ? "" : raw.trim();
            if (!value.isEmpty()) {
                buckets.add(value.toUpperCase(Locale.ROOT));
            }
        }
        return buckets.isEmpty() ? defaultPhase31Buckets() : List.copyOf(buckets);
    }

    private static <T> T readOptionalJson(Path path, Class<T> type) {
        try {
            if (path == null || Files.notExists(path)) {
                return null;
            }
            return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read governance artifact " + path, e);
        }
    }

    public record CommandResult(int exitCode, String output) {
        public boolean success() {
            return exitCode == 0;
        }
    }
}
