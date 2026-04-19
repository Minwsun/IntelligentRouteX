package com.routechain.v2.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DispatchQualityArtifactWriter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private DispatchQualityArtifactWriter() {
    }

    public static BenchmarkArtifacts writeBenchmarkRun(DispatchQualityBenchmarkRun run, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        List<Path> rawJsonPaths = new ArrayList<>();
        List<Path> rawMarkdownPaths = new ArrayList<>();
        for (DispatchQualityBenchmarkResult result : run.rawResults()) {
            String stem = benchmarkStem(result);
            Path jsonPath = outputDirectory.resolve(stem + ".json");
            Path markdownPath = outputDirectory.resolve(stem + ".md");
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), result);
            Files.writeString(markdownPath, markdownForBenchmarkResult(result));
            rawJsonPaths.add(jsonPath);
            rawMarkdownPaths.add(markdownPath);
        }

        Path comparisonJsonPath = null;
        Path comparisonMarkdownPath = null;
        Path comparisonCsvPath = null;
        if (run.comparisonReport() != null) {
            String stem = comparisonStem(run.comparisonReport());
            comparisonJsonPath = outputDirectory.resolve(stem + ".json");
            comparisonMarkdownPath = outputDirectory.resolve(stem + ".md");
            comparisonCsvPath = outputDirectory.resolve(stem + ".csv");
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(comparisonJsonPath.toFile(), run.comparisonReport());
            Files.writeString(comparisonMarkdownPath, markdownForComparison(run.comparisonReport()));
            Files.writeString(comparisonCsvPath, csvForComparison(run.comparisonReport()));
        }

        return new BenchmarkArtifacts(
                List.copyOf(rawJsonPaths),
                List.copyOf(rawMarkdownPaths),
                comparisonJsonPath,
                comparisonMarkdownPath,
                comparisonCsvPath);
    }

    public static AblationArtifacts writeAblationResult(DispatchAblationResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        String stem = ablationStem(result);
        Path jsonPath = outputDirectory.resolve(stem + ".json");
        Path markdownPath = outputDirectory.resolve(stem + ".md");
        Path csvPath = outputDirectory.resolve(stem + ".csv");
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), result);
        Files.writeString(markdownPath, markdownForAblation(result));
        Files.writeString(csvPath, csvForAblation(result));
        return new AblationArtifacts(jsonPath, markdownPath, csvPath);
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

    public static DispatchQualityComparisonReport readComparisonReport(Path path) throws IOException {
        return OBJECT_MAPPER.readValue(path.toFile(), DispatchQualityComparisonReport.class);
    }

    private static String markdownForBenchmarkResult(DispatchQualityBenchmarkResult result) {
        DispatchQualityMetrics metrics = result.metrics();
        StringBuilder builder = new StringBuilder("# Dispatch Quality Benchmark\n\n");
        builder.append("- baseline: `").append(result.baselineId()).append("`\n");
        builder.append("- scenario: `").append(result.scenarioPack()).append(" / ").append(result.scenarioName()).append("`\n");
        builder.append("- workload: `").append(result.workloadSize()).append("`\n");
        builder.append("- execution mode: `").append(result.executionMode()).append("`\n");
        builder.append("- authority class: `").append(result.runAuthorityClass()).append("`\n");
        builder.append("- authority eligible: `").append(result.authorityEligible()).append("`\n");
        builder.append("- model manifest: `").append(result.resolvedModelManifestPath()).append("`\n");
        builder.append("- manifest exists: `").append(result.manifestExists()).append("`\n");
        builder.append("- ml attach status: `").append(result.mlAttachStatus()).append("`\n");
        builder.append("- selected proposals: `").append(metrics.selectedProposalCount()).append("`\n");
        builder.append("- executed assignments: `").append(metrics.executedAssignmentCount()).append("`\n");
        builder.append("- conflict free: `").append(metrics.conflictFreeAssignments()).append("`\n");
        builder.append("- bundle rate: `").append(metrics.bundleRate()).append("`\n");
        builder.append("- robust utility avg: `").append(metrics.robustUtilityAverage()).append("`\n");
        builder.append("- selector objective: `").append(metrics.selectorObjectiveValue()).append("`\n");
        if (!result.workerBaseUrls().isEmpty()) {
            builder.append("- worker base urls: `").append(result.workerBaseUrls()).append("`\n");
        }
        if (!result.activeMlFlags().isEmpty()) {
            builder.append("- active ml flags: `").append(result.activeMlFlags()).append("`\n");
        }
        if (!result.mlAttachmentFailureReasons().isEmpty()) {
            builder.append("- ml attachment failure reasons: `").append(result.mlAttachmentFailureReasons()).append("`\n");
        }
        if (!result.workerStatusSnapshot().isEmpty()) {
            builder.append("\n## Worker Attachment Snapshot\n\n");
            for (DispatchQualityWorkerStatus worker : result.workerStatusSnapshot()) {
                builder.append("- `").append(worker.workerName()).append("` ")
                        .append("enabled=`").append(worker.enabled()).append("` ")
                        .append("ready=`").append(worker.ready()).append("` ")
                        .append("reachable=`").append(worker.reachable()).append("` ")
                        .append("applied=`").append(worker.applied()).append("` ")
                        .append("reason=`").append(worker.readyReason()).append("` ")
                        .append("notAppliedReason=`").append(worker.notAppliedReason()).append("` ")
                        .append("baseUrl=`").append(worker.baseUrl()).append("`")
                        .append('\n');
            }
        }
        if (!result.notes().isEmpty()) {
            builder.append("- notes: `").append(result.notes()).append("`\n");
        }
        return builder.toString();
    }

    private static String markdownForComparison(DispatchQualityComparisonReport report) {
        StringBuilder builder = new StringBuilder("# Dispatch Quality Comparison\n\n");
        builder.append("- scenario: `").append(report.scenarioPack()).append(" / ").append(report.scenarioName()).append("`\n");
        builder.append("- workload: `").append(report.workloadSize()).append("`\n");
        builder.append("- execution mode: `").append(report.executionMode()).append("`\n");
        builder.append("- authority class: `").append(report.runAuthorityClass()).append("`\n");
        builder.append("- authority eligible: `").append(report.authorityEligible()).append("`\n");
        builder.append("- summary: ").append(report.comparisonSummary()).append("\n\n");
        builder.append("## Full V2 Advantages\n\n");
        if (report.fullV2Advantages().isEmpty()) {
            builder.append("- none\n");
        } else {
            for (String advantage : report.fullV2Advantages()) {
                builder.append("- ").append(advantage).append('\n');
            }
        }
        builder.append("\n## Full V2 Regressions\n\n");
        if (report.fullV2Regressions().isEmpty()) {
            builder.append("- none\n");
        } else {
            for (String regression : report.fullV2Regressions()) {
                builder.append("- ").append(regression).append('\n');
            }
        }
        return builder.toString();
    }

    private static String markdownForAblation(DispatchAblationResult result) {
        StringBuilder builder = new StringBuilder("# Dispatch Quality Ablation\n\n");
        builder.append("- component: `").append(result.toggledComponent()).append("`\n");
        builder.append("- scenario: `").append(result.scenarioPack()).append(" / ").append(result.scenarioName()).append("`\n");
        builder.append("- workload: `").append(result.workloadSize()).append("`\n");
        builder.append("- execution mode: `").append(result.executionMode()).append("`\n");
        builder.append("\n## Delta Summary\n\n");
        for (String line : result.deltaSummary()) {
            builder.append("- ").append(line).append('\n');
        }
        return builder.toString();
    }

    private static String csvForComparison(DispatchQualityComparisonReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("baseline,scenarioPack,scenarioName,workloadSize,executionMode,selectedProposalCount,executedAssignmentCount,conflictFreeAssignments,bundleRate,averageBundleSize,routeFallbackRate,averageProjectedPickupEtaMinutes,averageProjectedCompletionEtaMinutes,landingValueAverage,robustUtilityAverage,selectorObjectiveValue,degradeRate,workerFallbackRate,liveSourceFallbackRate\n");
        for (DispatchQualityBenchmarkResult result : report.baselineResults()) {
            DispatchQualityMetrics metrics = result.metrics();
            builder.append(csv(result.baselineId())).append(',')
                    .append(csv(result.scenarioPack())).append(',')
                    .append(csv(result.scenarioName())).append(',')
                    .append(csv(result.workloadSize())).append(',')
                    .append(csv(result.executionMode())).append(',')
                    .append(metrics.selectedProposalCount()).append(',')
                    .append(metrics.executedAssignmentCount()).append(',')
                    .append(metrics.conflictFreeAssignments()).append(',')
                    .append(metrics.bundleRate()).append(',')
                    .append(metrics.averageBundleSize()).append(',')
                    .append(metrics.routeFallbackRate()).append(',')
                    .append(metrics.averageProjectedPickupEtaMinutes()).append(',')
                    .append(metrics.averageProjectedCompletionEtaMinutes()).append(',')
                    .append(metrics.landingValueAverage()).append(',')
                    .append(metrics.robustUtilityAverage()).append(',')
                    .append(metrics.selectorObjectiveValue()).append(',')
                    .append(metrics.degradeRate()).append(',')
                    .append(metrics.workerFallbackRate()).append(',')
                    .append(metrics.liveSourceFallbackRate()).append('\n');
        }
        return builder.toString();
    }

    private static String csvForAblation(DispatchAblationResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("variant,scenarioPack,scenarioName,workloadSize,executionMode,toggledComponent,selectedProposalCount,executedAssignmentCount,conflictFreeAssignments,bundleRate,averageBundleSize,routeFallbackRate,averageProjectedPickupEtaMinutes,averageProjectedCompletionEtaMinutes,landingValueAverage,robustUtilityAverage,selectorObjectiveValue,degradeRate,workerFallbackRate,liveSourceFallbackRate\n");
        appendMetricsRow(builder, "control", result, result.controlMetrics());
        appendMetricsRow(builder, "variant", result, result.variantMetrics());
        return builder.toString();
    }

    private static void appendMetricsRow(StringBuilder builder,
                                         String variantName,
                                         DispatchAblationResult result,
                                         DispatchQualityMetrics metrics) {
        builder.append(csv(variantName)).append(',')
                .append(csv(result.scenarioPack())).append(',')
                .append(csv(result.scenarioName())).append(',')
                .append(csv(result.workloadSize())).append(',')
                .append(csv(result.executionMode())).append(',')
                .append(csv(result.toggledComponent())).append(',')
                .append(metrics.selectedProposalCount()).append(',')
                .append(metrics.executedAssignmentCount()).append(',')
                .append(metrics.conflictFreeAssignments()).append(',')
                .append(metrics.bundleRate()).append(',')
                .append(metrics.averageBundleSize()).append(',')
                .append(metrics.routeFallbackRate()).append(',')
                .append(metrics.averageProjectedPickupEtaMinutes()).append(',')
                .append(metrics.averageProjectedCompletionEtaMinutes()).append(',')
                .append(metrics.landingValueAverage()).append(',')
                .append(metrics.robustUtilityAverage()).append(',')
                .append(metrics.selectorObjectiveValue()).append(',')
                .append(metrics.degradeRate()).append(',')
                .append(metrics.workerFallbackRate()).append(',')
                .append(metrics.liveSourceFallbackRate()).append('\n');
    }

    private static String benchmarkStem(DispatchQualityBenchmarkResult result) {
        return "dispatch-quality-%s-%s-%s-%s-%s".formatted(
                result.scenarioPack().toLowerCase(),
                result.workloadSize().toLowerCase(),
                result.executionMode().toLowerCase(),
                result.baselineId().toLowerCase(),
                FILE_TS.format(result.benchmarkTimestamp()));
    }

    private static String comparisonStem(DispatchQualityComparisonReport report) {
        return "dispatch-quality-compare-%s-%s-%s-%s".formatted(
                report.scenarioPack().toLowerCase(),
                report.workloadSize().toLowerCase(),
                report.executionMode().toLowerCase(),
                FILE_TS.format(java.time.Instant.now()));
    }

    private static String ablationStem(DispatchAblationResult result) {
        return "dispatch-quality-ablation-%s-%s-%s-%s".formatted(
                result.toggledComponent().toLowerCase().replace('_', '-'),
                result.scenarioPack().toLowerCase(),
                result.workloadSize().toLowerCase(),
                FILE_TS.format(java.time.Instant.now()));
    }

    private static String csv(String value) {
        String escaped = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    public record BenchmarkArtifacts(
            List<Path> rawJsonPaths,
            List<Path> rawMarkdownPaths,
            Path comparisonJsonPath,
            Path comparisonMarkdownPath,
            Path comparisonCsvPath) {
    }

    public record AblationArtifacts(
            Path jsonPath,
            Path markdownPath,
            Path csvPath) {
    }
}
