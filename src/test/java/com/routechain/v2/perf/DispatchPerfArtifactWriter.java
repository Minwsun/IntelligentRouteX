package com.routechain.v2.perf;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public final class DispatchPerfArtifactWriter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private DispatchPerfArtifactWriter() {
    }

    public static ArtifactPaths writeResult(DispatchPerfBenchmarkResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        String fileStem = fileStem(result);
        Path jsonPath = outputDirectory.resolve(fileStem + ".json");
        Path markdownPath = outputDirectory.resolve(fileStem + ".md");
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), result);
        Files.writeString(markdownPath, markdownForResult(result));
        return new ArtifactPaths(jsonPath, markdownPath);
    }

    public static Path writeSummary(DispatchPerfBenchmarkSummary summary, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Path summaryPath = outputDirectory.resolve("dispatch-perf-summary.md");
        Files.writeString(summaryPath, markdownForSummary(summary));
        return summaryPath;
    }

    public static DispatchPerfBenchmarkResult readResult(Path path) throws IOException {
        return OBJECT_MAPPER.readValue(path.toFile(), DispatchPerfBenchmarkResult.class);
    }

    public static List<Path> jsonArtifacts(Path outputDirectory) throws IOException {
        if (!Files.exists(outputDirectory)) {
            return List.of();
        }
        try (var stream = Files.list(outputDirectory)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static String markdownForResult(DispatchPerfBenchmarkResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Dispatch Perf Benchmark\n\n");
        builder.append("- baseline: `").append(result.baselineId()).append("`\n");
        builder.append("- workload: `").append(result.workloadSize()).append("` (`")
                .append(result.orderCount()).append("` orders / `")
                .append(result.driverCount()).append("` drivers)\n");
        builder.append("- mode: `").append(result.runMode()).append("`\n");
        builder.append("- selector mode: `").append(result.selectorMode()).append("`\n");
        builder.append("- git commit: `").append(result.gitCommit()).append("`\n");
        builder.append("- machine: `").append(result.machineProfile().machineLabel()).append("`\n");
        builder.append("- total latency p50/p95/p99: `")
                .append(result.totalLatencyStats().p50Ms()).append(" / ")
                .append(result.totalLatencyStats().p95Ms()).append(" / ")
                .append(result.totalLatencyStats().p99Ms()).append(" ms`\n");
        builder.append("- budget breach rate: `").append(result.budgetBreachRate()).append("`\n");
        builder.append("- deferred: `").append(result.deferred()).append("`\n");
        if (!result.reusedStageNames().isEmpty()) {
            builder.append("- reused stages: `").append(result.reusedStageNames()).append("`\n");
        }
        if (!result.notes().isEmpty()) {
            builder.append("- notes: `").append(result.notes()).append("`\n");
        }
        builder.append("\n## Stage p50/p95\n\n");
        for (DispatchPerfStageLatencyStats stage : result.stageLatencyStats()) {
            builder.append("- `").append(stage.stageName()).append("`: `")
                    .append(stage.latencyStats().p50Ms()).append(" / ")
                    .append(stage.latencyStats().p95Ms()).append(" ms`\n");
        }
        return builder.toString();
    }

    private static String markdownForSummary(DispatchPerfBenchmarkSummary summary) {
        StringBuilder builder = new StringBuilder("# Dispatch Perf Summary\n\n");
        builder.append("- generated at: `").append(summary.generatedAt()).append("`\n");
        builder.append("- result count: `").append(summary.resultCount()).append("`\n\n");
        for (DispatchPerfBenchmarkResult result : summary.results()) {
            builder.append("## `").append(result.baselineId()).append(" / ")
                    .append(result.workloadSize()).append(" / ")
                    .append(result.runMode()).append("`\n\n");
            builder.append("- total latency p50/p95/p99: `")
                    .append(result.totalLatencyStats().p50Ms()).append(" / ")
                    .append(result.totalLatencyStats().p95Ms()).append(" / ")
                    .append(result.totalLatencyStats().p99Ms()).append(" ms`\n");
            builder.append("- budget breach rate: `").append(result.budgetBreachRate()).append("`\n");
            builder.append("- deferred: `").append(result.deferred()).append("`\n\n");
        }
        if (!summary.notes().isEmpty()) {
            builder.append("## Notes\n\n");
            for (String note : summary.notes()) {
                builder.append("- ").append(note).append('\n');
            }
        }
        return builder.toString();
    }

    private static String fileStem(DispatchPerfBenchmarkResult result) {
        String commit = result.gitCommit() == null || result.gitCommit().isBlank() ? "workspace" : result.gitCommit();
        return "dispatch-perf-%s-%s-%s-%s-%s".formatted(
                result.baselineId().toLowerCase(),
                result.workloadSize().toLowerCase(),
                result.runMode().toLowerCase(),
                commit,
                FILE_TS.format(result.benchmarkTimestamp()));
    }

    public record ArtifactPaths(Path jsonPath, Path markdownPath) {
    }
}
