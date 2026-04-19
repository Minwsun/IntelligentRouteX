package com.routechain.v2.chaos;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public final class DispatchStabilityArtifactWriter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private DispatchStabilityArtifactWriter() {
    }

    public static ArtifactPaths writeLargeScaleResult(DispatchLargeScaleBenchmarkResult result, Path outputDirectory) throws IOException {
        return write(result, outputDirectory, "dispatch-large-scale-%s-%s-%s-%s".formatted(
                result.baselineId().toLowerCase(),
                result.scenarioPack().toLowerCase(),
                result.workloadSize().toLowerCase(),
                FILE_TS.format(result.benchmarkTimestamp())),
                markdownForLargeScale(result));
    }

    public static ArtifactPaths writeSoakResult(DispatchSoakRunResult result, Path outputDirectory) throws IOException {
        return write(result, outputDirectory, "dispatch-soak-%s-%s-%s".formatted(
                result.scenarioPack().toLowerCase(),
                result.workloadSize().toLowerCase(),
                FILE_TS.format(result.benchmarkTimestamp())),
                markdownForSoak(result));
    }

    public static ArtifactPaths writeChaosResult(DispatchChaosRunResult result, Path outputDirectory) throws IOException {
        return write(result, outputDirectory, "dispatch-chaos-%s-%s-%s".formatted(
                result.faultType().toLowerCase(),
                result.workloadSize().toLowerCase(),
                FILE_TS.format(result.benchmarkTimestamp())),
                markdownForChaos(result));
    }

    public static Path writeSummary(DispatchStabilitySummary summary, Path outputDirectory, String fileName) throws IOException {
        Files.createDirectories(outputDirectory);
        Path path = outputDirectory.resolve(fileName);
        StringBuilder builder = new StringBuilder("# Dispatch Stability Summary\n\n");
        builder.append("- suite type: `").append(summary.suiteType()).append("`\n");
        builder.append("- scenario count: `").append(summary.scenarioCount()).append("`\n");
        builder.append("- passed: `").append(summary.passedScenarioCount()).append("`\n");
        builder.append("- failed: `").append(summary.failedScenarioCount()).append("`\n\n");
        for (DispatchStabilityScenarioOutcome outcome : summary.scenarioResults()) {
            builder.append("## `").append(outcome.scenarioKey()).append("`\n\n");
            builder.append("- passed: `").append(outcome.passed()).append("`\n");
            if (!outcome.notes().isEmpty()) {
                builder.append("- notes: `").append(outcome.notes()).append("`\n");
            }
            builder.append('\n');
        }
        if (!summary.failureSummaries().isEmpty()) {
            builder.append("## Failure Summaries\n\n");
            for (String failure : summary.failureSummaries()) {
                builder.append("- ").append(failure).append('\n');
            }
        }
        Files.writeString(path, builder.toString());
        return path;
    }

    public static List<Path> jsonArtifacts(Path outputDirectory) throws IOException {
        if (!Files.exists(outputDirectory)) {
            return List.of();
        }
        try (var stream = Files.list(outputDirectory)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static ArtifactPaths write(Object value, Path outputDirectory, String stem, String markdown) throws IOException {
        Files.createDirectories(outputDirectory);
        Path jsonPath = outputDirectory.resolve(stem + ".json");
        Path markdownPath = outputDirectory.resolve(stem + ".md");
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), value);
        Files.writeString(markdownPath, markdown);
        return new ArtifactPaths(jsonPath, markdownPath);
    }

    private static String markdownForLargeScale(DispatchLargeScaleBenchmarkResult result) {
        StringBuilder builder = new StringBuilder("# Dispatch Large-Scale Benchmark\n\n");
        builder.append("- baseline: `").append(result.baselineId()).append("`\n");
        builder.append("- scenario: `").append(result.scenarioPack()).append("`\n");
        builder.append("- workload: `").append(result.workloadSize()).append("`\n");
        builder.append("- execution mode: `").append(result.executionMode()).append("`\n");
        builder.append("- total latency p50/p95/p99: `")
                .append(result.totalLatencyStats().p50Ms()).append(" / ")
                .append(result.totalLatencyStats().p95Ms()).append(" / ")
                .append(result.totalLatencyStats().p99Ms()).append(" ms`\n");
        builder.append("- passed: `").append(result.passed()).append("`\n");
        if (!result.notes().isEmpty()) {
            builder.append("- notes: `").append(result.notes()).append("`\n");
        }
        return builder.toString();
    }

    private static String markdownForSoak(DispatchSoakRunResult result) {
        StringBuilder builder = new StringBuilder("# Dispatch Soak Run\n\n");
        builder.append("- scenario: `").append(result.scenarioPack()).append("`\n");
        builder.append("- workload: `").append(result.workloadSize()).append("`\n");
        builder.append("- duration profile: `").append(result.durationProfile()).append("`\n");
        builder.append("- authority class: `").append(result.runAuthorityClass()).append("`\n");
        builder.append("- authority eligible: `").append(result.authorityEligible()).append("`\n");
        builder.append("- sample override applied: `").append(result.sampleCountOverrideApplied()).append("`\n");
        builder.append("- sample count: `").append(result.sampleCount()).append("`\n");
        builder.append("- replay isolation maintained: `").append(result.replayIsolationMaintained()).append("`\n");
        builder.append("- snapshot stability: `").append(result.snapshotStability()).append("`\n");
        builder.append("- passed: `").append(result.passed()).append("`\n");
        return builder.toString();
    }

    private static String markdownForChaos(DispatchChaosRunResult result) {
        StringBuilder builder = new StringBuilder("# Dispatch Chaos Run\n\n");
        builder.append("- fault: `").append(result.faultType()).append("`\n");
        builder.append("- scenario: `").append(result.scenarioPack()).append("`\n");
        builder.append("- workload: `").append(result.workloadSize()).append("`\n");
        builder.append("- dispatch count: `").append(result.dispatchCount()).append("`\n");
        builder.append("- deferred: `").append(result.deferred()).append("`\n");
        builder.append("- passed: `").append(result.passed()).append("`\n");
        if (!result.degradeReasons().isEmpty()) {
            builder.append("- degrade reasons: `").append(result.degradeReasons()).append("`\n");
        }
        if (!result.notes().isEmpty()) {
            builder.append("- notes: `").append(result.notes()).append("`\n");
        }
        return builder.toString();
    }

    public record ArtifactPaths(Path jsonPath, Path markdownPath) {
    }
}
