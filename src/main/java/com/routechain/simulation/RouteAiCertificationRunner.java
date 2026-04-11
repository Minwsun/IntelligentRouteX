package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.infra.ArtifactPaths;
import com.routechain.infra.GsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads benchmark artifacts and emits a stable absolute-gate route certification summary.
 */
public final class RouteAiCertificationRunner {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path ROOT = ArtifactPaths.benchmarksRoot();
    private static final Path RUNTIME_SLO_JSON = ROOT.resolve("runtime_slo_summary.json");
    private static final Path COMPARES_DIR = ROOT.resolve("compares");
    private static final Path RUNS_DIR = ROOT.resolve("runs");
    private static final Path STAGE_LATENCY_CSV = ROOT.resolve("dispatch_stage_breakdown.csv");

    private RouteAiCertificationRunner() {}

    public static void main(String[] args) {
        String laneName = args.length > 0 && !args[0].isBlank()
                ? args[0].trim().toLowerCase()
                : "smoke";
        BenchmarkCertificationScenarioMatrix.LaneDefinition lane =
                BenchmarkCertificationConfigLoader.loadScenarioMatrix().lane(laneName);
        BenchmarkCertificationScenarioMatrix.ScenarioBucket primaryBucket = lane.scenarioBuckets().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Lane " + laneName + " has no scenario buckets"));
        BenchmarkCertificationBaseline.ScenarioGroupThresholds thresholds =
                BenchmarkCertificationConfigLoader.loadBaseline().thresholdsFor(primaryBucket.scenarioGroup());
        RuntimeSloSummary runtime = readRuntimeSloSummary();
        ReplayCompareResult compare = readLatestCompare(primaryBucket);
        CsvRow stageRow = readLatestRunStageRow();
        ScenarioAggregate aggregate = aggregate(primaryBucket, readRunReports());

        boolean routeRegressionPass = true;
        boolean dispatchP95Pass = runtime.measurementValid()
                && runtime.dispatchP95Ms() <= thresholds.maxDispatchP95Ms();
        boolean dispatchP99Pass = runtime.measurementValid()
                && runtime.dispatchP99Ms() <= thresholds.maxDispatchP99Ms();
        boolean scenarioKpiPass = aggregate.completionRate() >= thresholds.minCompletionRate()
                && aggregate.onTimeRate() >= thresholds.minOnTimeRate()
                && aggregate.realAssignmentRate() >= thresholds.minRealAssignmentRate()
                && aggregate.deadheadDistanceRatio() <= thresholds.maxDeadheadDistanceRatio()
                && aggregate.deadheadPerCompletedOrderKm() <= thresholds.maxDeadheadDistancePerCompleted()
                && aggregate.deliveryCorridorQuality() >= thresholds.minDeliveryCorridorQuality()
                && aggregate.lastDropGoodZoneRate() >= thresholds.minLastDropGoodZoneRate()
                && aggregate.postDropOrderHitRate() >= thresholds.minPostDropOrderHitRate()
                && aggregate.zigZagPenaltyAvg() <= thresholds.maxZigZagPenalty()
                && aggregate.avgAssignedDeadheadKm() <= thresholds.maxAverageAssignedDeadheadKm()
                && aggregate.fallbackExecutedShare() <= thresholds.maxFallbackExecutedShare()
                && aggregate.borrowedCoverageExecutedShare() <= thresholds.maxBorrowedCoverageExecutedShare()
                && aggregate.selectedSubThreeInCleanRate() <= thresholds.maxSelectedSubThreeInCleanRate();
        boolean safetyPass = aggregate.stressDowngradeRate() <= thresholds.maxStressDowngradeRate()
                && aggregate.cancellationRate() <= thresholds.maxCancellationRate()
                && aggregate.failedOrderRate() <= thresholds.maxFailedOrderRate()
                && aggregate.allSafetyAccepted();
        if (thresholds.maxNextOrderIdleMinutes() != null) {
            scenarioKpiPass = scenarioKpiPass
                    && aggregate.nextOrderIdleMinutes() <= thresholds.maxNextOrderIdleMinutes();
        }
        if (thresholds.maxExpectedPostCompletionEmptyKm() != null) {
            scenarioKpiPass = scenarioKpiPass
                    && aggregate.expectedPostCompletionEmptyKm() <= thresholds.maxExpectedPostCompletionEmptyKm();
        }
        boolean gainPass = compare.overallGainPercent() >= 0.0;
        boolean completionPass = compare.completionRateDelta() >= 0.0;
        boolean deadheadPass = compare.deadheadRatioDelta() <= 0.0;
        boolean overallPass = routeRegressionPass
                && runtime.measurementValid()
                && dispatchP95Pass
                && dispatchP99Pass
                && scenarioKpiPass
                && safetyPass;

        List<String> notes = new ArrayList<>();
        if (!dispatchP95Pass) {
            notes.add("dispatchP95 above " + formatTarget(thresholds.maxDispatchP95Ms()) + "ms absolute target");
        }
        if (!dispatchP99Pass) {
            notes.add("dispatchP99 above " + formatTarget(thresholds.maxDispatchP99Ms()) + "ms absolute target");
        }
        if (!scenarioKpiPass) {
            notes.addAll(aggregate.failureNotes(thresholds));
        }
        if (!safetyPass) {
            notes.add("Scenario safety guardrails are outside the committed baseline");
        }
        if (!gainPass || !completionPass || !deadheadPass) {
            notes.add("Legacy reference is below Omega on one or more major deltas; warning only");
        }
        if (stageRow != null) {
            notes.add("dominant dispatch stage by p95: " + stageRow.value("dominantStageByP95"));
        }
        notes.add("absolute baseline group=" + primaryBucket.scenarioGroup()
                + " samples=" + aggregate.sampleCount()
                + " seeds=" + aggregate.observedSeeds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining("|")));

        RouteAiCertificationSummary summary = new RouteAiCertificationSummary(
                BenchmarkSchema.VERSION,
                "route-ai-certification-" + laneName,
                runtime.profileName(),
                primaryBucket.scenarioGroup(),
                "Legacy",
                "Omega-current",
                routeRegressionPass,
                runtime.measurementValid(),
                runtime.dispatchP95Ms(),
                runtime.dispatchP99Ms(),
                thresholds.maxDispatchP95Ms(),
                thresholds.maxDispatchP99Ms(),
                dispatchP95Pass,
                dispatchP99Pass,
                compare.overallGainPercent(),
                compare.completionRateDelta(),
                compare.deadheadRatioDelta(),
                compare.postDropOrderHitRateDelta(),
                gainPass,
                completionPass,
                deadheadPass,
                safetyPass,
                overallPass,
                stageRow == null ? "unknown" : stageRow.value("dominantStageByP95"),
                notes
        );

        BenchmarkArtifactWriter.writeRouteAiCertificationSummary(summary);
        System.out.println("[RouteAiCertification] lane=" + laneName
                + " verdict=" + (summary.overallPass() ? "PASS" : "FAIL")
                + " p95=" + String.format("%.1f", summary.dispatchP95Ms())
                + " p99=" + String.format("%.1f", summary.dispatchP99Ms())
                + " gain=" + String.format("%.1f", summary.overallGainPercent())
                + " completionDelta=" + String.format("%+.2f", summary.completionDelta())
                + " deadheadDelta=" + String.format("%+.2f", summary.deadheadDistanceRatioDelta()));
        if (!summary.overallPass()) {
            throw new IllegalStateException("Route AI certification failed for lane " + laneName);
        }
    }

    private static RuntimeSloSummary readRuntimeSloSummary() {
        try {
            if (Files.notExists(RUNTIME_SLO_JSON)) {
                throw new IllegalStateException("Missing runtime SLO artifact at " + RUNTIME_SLO_JSON);
            }
            return GSON.fromJson(Files.readString(RUNTIME_SLO_JSON, StandardCharsets.UTF_8), RuntimeSloSummary.class);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read runtime SLO summary", e);
        }
    }

    private static ReplayCompareResult readLatestCompare(BenchmarkCertificationScenarioMatrix.ScenarioBucket bucket) {
        List<ReplayCompareResult> compares = readCompareResults();
        for (int i = compares.size() - 1; i >= 0; i--) {
            ReplayCompareResult compare = compares.get(i);
            if (BenchmarkCertificationSupport.isCurrentOmegaCompare(compare)
                    && BenchmarkCertificationSupport.matchesScenario(compare.scenarioA(), bucket.scenarioMatchers())) {
                return compare;
            }
        }
        throw new IllegalStateException("Missing Legacy vs Omega-current compare artifact for bucket "
                + bucket.scenarioGroup());
    }

    private static CsvRow readLatestRunStageRow() {
        List<CsvRow> rows = readCsv(STAGE_LATENCY_CSV);
        for (int i = rows.size() - 1; i >= 0; i--) {
            CsvRow row = rows.get(i);
            if (row.value("scope").startsWith("run_")) {
                return row;
            }
        }
        return null;
    }

    private static List<RunReport> readRunReports() {
        try {
            if (Files.notExists(RUNS_DIR)) {
                return List.of();
            }
            List<RunReport> reports = new ArrayList<>();
            try (var stream = Files.list(RUNS_DIR)) {
                for (Path path : stream.filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
                    reports.add(GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), RunReport.class));
                }
            }
            return reports;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read run artifacts", e);
        }
    }

    private static List<ReplayCompareResult> readCompareResults() {
        try {
            if (Files.notExists(COMPARES_DIR)) {
                return List.of();
            }
            List<ReplayCompareResult> compares = new ArrayList<>();
            try (var stream = Files.list(COMPARES_DIR)) {
                for (Path path : stream.filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
                    compares.add(GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), ReplayCompareResult.class));
                }
            }
            return compares;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read compare artifacts", e);
        }
    }

    private static List<CsvRow> readCsv(Path path) {
        try {
            if (Files.notExists(path)) {
                return List.of();
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return List.of();
            }
            String[] headers = lines.get(0).split(",", -1);
            List<CsvRow> rows = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).isBlank()) {
                    continue;
                }
                String[] values = lines.get(i).split(",", -1);
                Map<String, String> row = new LinkedHashMap<>();
                for (int j = 0; j < headers.length; j++) {
                    row.put(headers[j], j < values.length ? values[j] : "");
                }
                rows.add(new CsvRow(row));
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read csv artifact " + path, e);
        }
    }

    private static ScenarioAggregate aggregate(BenchmarkCertificationScenarioMatrix.ScenarioBucket bucket,
                                               List<RunReport> runs) {
        List<RunReport> matched = runs.stream()
                .filter(BenchmarkCertificationSupport::isCurrentOmegaRun)
                .filter(run -> BenchmarkCertificationSupport.matchesScenario(run.scenarioName(), bucket.scenarioMatchers()))
                .toList();
        if (matched.isEmpty()) {
            throw new IllegalStateException("Missing current Omega run artifacts for bucket " + bucket.scenarioGroup());
        }
        List<Long> observedSeeds = matched.stream()
                .map(RunReport::seed)
                .distinct()
                .sorted()
                .toList();
        return new ScenarioAggregate(
                matched.size(),
                observedSeeds,
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.completionRate()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.onTimeRate()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.cancellationRate()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.failedOrderRate()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.realAssignmentRate()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.deadheadDistanceRatio()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.deadheadPerCompletedOrderKm()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.postDropOrderHitRate()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.deliveryCorridorQuality()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.lastDropGoodZoneRate()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.zigZagPenaltyAvg()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.avgAssignedDeadheadKm()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.fallbackDirect()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.borrowedExec()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.selectedSubThreeRateInCleanRegime()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.stressDowngradeRate()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.nextOrderIdleMinutes()).toList()),
                BenchmarkCertificationSupport.average(matched.stream().map(run -> run.expectedPostCompletionEmptyKm()).toList()),
                matched.stream().allMatch(run -> run.acceptance().safetyPass())
        );
    }

    private static String formatTarget(double value) {
        return String.format("%.0f", value);
    }

    private record CsvRow(Map<String, String> values) {
        private String value(String key) {
            return values.getOrDefault(key, "");
        }

        private double doubleValue(String key) {
            String value = value(key);
            if (value == null || value.isBlank()) {
                return 0.0;
            }
            return Double.parseDouble(value);
        }
    }

    private record ScenarioAggregate(
            int sampleCount,
            List<Long> observedSeeds,
            double completionRate,
            double onTimeRate,
            double cancellationRate,
            double failedOrderRate,
            double realAssignmentRate,
            double deadheadDistanceRatio,
            double deadheadPerCompletedOrderKm,
            double postDropOrderHitRate,
            double deliveryCorridorQuality,
            double lastDropGoodZoneRate,
            double zigZagPenaltyAvg,
            double avgAssignedDeadheadKm,
            double fallbackExecutedShare,
            double borrowedCoverageExecutedShare,
            double selectedSubThreeInCleanRate,
            double stressDowngradeRate,
            double nextOrderIdleMinutes,
            double expectedPostCompletionEmptyKm,
            boolean allSafetyAccepted
    ) {
        private List<String> failureNotes(BenchmarkCertificationBaseline.ScenarioGroupThresholds thresholds) {
            List<String> notes = new ArrayList<>();
            if (completionRate < thresholds.minCompletionRate()) {
                notes.add("completion below " + thresholds.minCompletionRate() + "%");
            }
            if (onTimeRate < thresholds.minOnTimeRate()) {
                notes.add("onTime below " + thresholds.minOnTimeRate() + "%");
            }
            if (realAssignmentRate < thresholds.minRealAssignmentRate()) {
                notes.add("realAssign below " + thresholds.minRealAssignmentRate() + "%");
            }
            if (deadheadDistanceRatio > thresholds.maxDeadheadDistanceRatio()) {
                notes.add("deadhead ratio above " + thresholds.maxDeadheadDistanceRatio() + "%");
            }
            if (deadheadPerCompletedOrderKm > thresholds.maxDeadheadDistancePerCompleted()) {
                notes.add("deadhead per completed above " + thresholds.maxDeadheadDistancePerCompleted() + "km");
            }
            if (postDropOrderHitRate < thresholds.minPostDropOrderHitRate()) {
                notes.add("post-drop hit below " + thresholds.minPostDropOrderHitRate() + "%");
            }
            if (deliveryCorridorQuality < thresholds.minDeliveryCorridorQuality()) {
                notes.add("corridor quality below " + thresholds.minDeliveryCorridorQuality());
            }
            if (lastDropGoodZoneRate < thresholds.minLastDropGoodZoneRate()) {
                notes.add("good-last-zone below " + thresholds.minLastDropGoodZoneRate() + "%");
            }
            if (zigZagPenaltyAvg > thresholds.maxZigZagPenalty()) {
                notes.add("zigzag penalty above " + thresholds.maxZigZagPenalty());
            }
            if (avgAssignedDeadheadKm > thresholds.maxAverageAssignedDeadheadKm()) {
                notes.add("avg assigned deadhead above " + thresholds.maxAverageAssignedDeadheadKm() + "km");
            }
            if (fallbackExecutedShare > thresholds.maxFallbackExecutedShare()) {
                notes.add("fallback share above " + thresholds.maxFallbackExecutedShare() + "%");
            }
            if (borrowedCoverageExecutedShare > thresholds.maxBorrowedCoverageExecutedShare()) {
                notes.add("borrowed coverage share above "
                        + thresholds.maxBorrowedCoverageExecutedShare() + "%");
            }
            if (selectedSubThreeInCleanRate > thresholds.maxSelectedSubThreeInCleanRate()) {
                notes.add("selected sub-three above " + thresholds.maxSelectedSubThreeInCleanRate() + "%");
            }
            if (thresholds.maxNextOrderIdleMinutes() != null
                    && nextOrderIdleMinutes > thresholds.maxNextOrderIdleMinutes()) {
                notes.add("next-order idle above " + thresholds.maxNextOrderIdleMinutes() + "m");
            }
            if (thresholds.maxExpectedPostCompletionEmptyKm() != null
                    && expectedPostCompletionEmptyKm > thresholds.maxExpectedPostCompletionEmptyKm()) {
                notes.add("post-completion empty km above "
                        + thresholds.maxExpectedPostCompletionEmptyKm() + "km");
            }
            return notes;
        }
    }
}

