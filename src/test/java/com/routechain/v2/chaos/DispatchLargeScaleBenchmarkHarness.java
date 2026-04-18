package com.routechain.v2.chaos;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.benchmark.DispatchQualityMetrics;
import com.routechain.v2.perf.DispatchPerfBenchmarkHarness;
import com.routechain.v2.perf.DispatchPerfMachineProfile;
import com.routechain.v2.perf.DispatchPerfNumericStats;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DispatchLargeScaleBenchmarkHarness {
    public static final int DEFAULT_RUN_COUNT = 3;

    public List<DispatchLargeScaleBenchmarkResult> run(BenchmarkRequest request) {
        return request.baselines().stream()
                .map(baselineId -> runBaseline(request, baselineId))
                .toList();
    }

    public DispatchStabilitySummary summarize(List<DispatchLargeScaleBenchmarkResult> results) {
        List<DispatchStabilityScenarioOutcome> outcomes = results.stream()
                .map(result -> new DispatchStabilityScenarioOutcome(
                        "dispatch-stability-scenario-outcome/v1",
                        "%s/%s/%s".formatted(result.baselineId(), result.scenarioPack(), result.workloadSize()),
                        result.passed(),
                        result.notes()))
                .toList();
        List<String> failures = results.stream()
                .filter(result -> !result.passed())
                .map(result -> "large-scale-failed:%s:%s".formatted(result.baselineId(), result.scenarioPack()))
                .toList();
        long passed = results.stream().filter(DispatchLargeScaleBenchmarkResult::passed).count();
        return new DispatchStabilitySummary(
                "dispatch-stability-summary/v1",
                "large-scale",
                results.size(),
                (int) passed,
                results.size() - (int) passed,
                outcomes,
                failures);
    }

    private DispatchLargeScaleBenchmarkResult runBaseline(BenchmarkRequest request,
                                                          DispatchPerfBenchmarkHarness.BaselineId baselineId) {
        if (request.workloadSize() == DispatchPerfBenchmarkHarness.WorkloadSize.XL && !request.runDeferredXl()) {
            return deferredResult(request, baselineId, "deferred-on-current-machine");
        }

        DispatchPhase3Support.ScenarioDefinition scenario = DispatchPhase3Support.scenarioDefinition(request.scenarioPack());
        Path feedbackDirectory = request.outputRoot()
                .resolve("feedback")
                .resolve(request.scenarioPack().wireName())
                .resolve(request.workloadSize().name().toLowerCase(Locale.ROOT))
                .resolve(request.executionMode().wireName())
                .resolve(baselineId.name().toLowerCase(Locale.ROOT));
        RouteChainDispatchV2Properties properties = DispatchPhase3Support.baseProperties(
                baselineId,
                request.executionMode(),
                feedbackDirectory);
        scenario.configureProperties(properties, baselineId);
        DispatchPhase3Support.ScenarioDependencies dependencies = scenario.dependencies(request.executionMode(), baselineId, properties);

        List<DispatchV2Result> results = new ArrayList<>();
        List<DispatchQualityMetrics> metrics = new ArrayList<>();
        List<Long> totalLatencySamples = new ArrayList<>();
        List<Long> estimatedSavedSamples = new ArrayList<>();
        List<String> reusedStages = new ArrayList<>();
        Map<String, Integer> degradeCounts = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        if (request.executionMode() == DispatchPhase3Support.ExecutionMode.LOCAL_REAL) {
            notes.add("non-authoritative-local-real-run");
        }

        var harness = DispatchPhase3Support.harness(properties, dependencies);
        for (int runIndex = 0; runIndex < request.runCount(); runIndex++) {
            DispatchV2Request dispatchRequest = scenario.request(
                    request.workloadSize(),
                    traceId(request, baselineId, runIndex),
                    baselineId);
            DispatchV2Result result = harness.core().dispatch(dispatchRequest);
            results.add(result);
            metrics.add(DispatchPhase3Support.metricsFrom(result));
            totalLatencySamples.add(result.latencyBudgetSummary().totalDispatchLatencyMs());
            estimatedSavedSamples.add(result.hotStartState().estimatedSavedMs());
            reusedStages.addAll(result.hotStartState().reusedStageNames());
            for (String degradeReason : DispatchPhase3Support.distinctDegrades(result)) {
                degradeCounts.merge(degradeReason, 1, Integer::sum);
            }
        }

        double budgetBreaches = results.stream()
                .filter(result -> result.latencyBudgetSummary().totalBudgetBreached()
                        || result.stageLatencies().stream().anyMatch(stage -> stage.budgetBreached()))
                .count();
        DispatchQualityMetrics aggregateMetrics = DispatchPhase3Support.aggregateMetrics(metrics);
        boolean passed = results.stream().allMatch(result -> result.decisionStages().size() == 12)
                && results.stream().allMatch(DispatchPhase3Support::conflictFreeAssignments);

        return new DispatchLargeScaleBenchmarkResult(
                "dispatch-large-scale-benchmark-result/v1",
                Instant.now(),
                DispatchPhase3Support.gitCommit(),
                DispatchPerfMachineProfile.capture(request.machineLabel()),
                request.executionMode().wireName(),
                baselineId.name(),
                request.scenarioPack().wireName(),
                request.scenarioPack().wireName(),
                request.workloadSize().name(),
                request.runCount(),
                false,
                DispatchPerfNumericStats.fromSamples(totalLatencySamples),
                DispatchPhase3Support.stageLatencyStats(results),
                aggregateMetrics,
                results.isEmpty() ? 0.0 : budgetBreaches / results.size(),
                DispatchPhase3Support.frequencyMap(reusedStages),
                DispatchPerfNumericStats.fromSamples(estimatedSavedSamples),
                aggregateMetrics.workerFallbackRate(),
                aggregateMetrics.liveSourceFallbackRate(),
                Map.copyOf(degradeCounts),
                passed,
                List.copyOf(notes));
    }

    private DispatchLargeScaleBenchmarkResult deferredResult(BenchmarkRequest request,
                                                             DispatchPerfBenchmarkHarness.BaselineId baselineId,
                                                             String note) {
        return new DispatchLargeScaleBenchmarkResult(
                "dispatch-large-scale-benchmark-result/v1",
                Instant.now(),
                DispatchPhase3Support.gitCommit(),
                DispatchPerfMachineProfile.capture(request.machineLabel()),
                request.executionMode().wireName(),
                baselineId.name(),
                request.scenarioPack().wireName(),
                request.scenarioPack().wireName(),
                request.workloadSize().name(),
                0,
                true,
                DispatchPerfNumericStats.empty(),
                List.of(),
                DispatchPhase3Support.aggregateMetrics(List.of()),
                0.0,
                Map.of(),
                DispatchPerfNumericStats.empty(),
                0.0,
                0.0,
                Map.of(),
                true,
                List.of(note));
    }

    private String traceId(BenchmarkRequest request,
                           DispatchPerfBenchmarkHarness.BaselineId baselineId,
                           int runIndex) {
        return "large-scale-%s-%s-%s-%s-%d".formatted(
                baselineId.name().toLowerCase(Locale.ROOT),
                request.scenarioPack().wireName(),
                request.workloadSize().name().toLowerCase(Locale.ROOT),
                request.executionMode().wireName(),
                runIndex);
    }

    public record BenchmarkRequest(
            List<DispatchPerfBenchmarkHarness.BaselineId> baselines,
            DispatchPerfBenchmarkHarness.WorkloadSize workloadSize,
            DispatchPhase3Support.ScenarioPack scenarioPack,
            DispatchPhase3Support.ExecutionMode executionMode,
            int runCount,
            String machineLabel,
            boolean runDeferredXl,
            Path outputRoot) {
    }
}
