package com.routechain.v2.benchmark;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.LiveStageMetadata;
import com.routechain.v2.MlStageMetadata;
import com.routechain.v2.TestDispatchV2Factory;
import com.routechain.v2.certification.DispatchHotStartCertificationHarness;
import com.routechain.v2.context.TrafficRefineMapper;
import com.routechain.v2.feedback.FeedbackStorageMode;
import com.routechain.v2.integration.ForecastClient;
import com.routechain.v2.integration.GreedRlClient;
import com.routechain.v2.integration.HttpForecastClient;
import com.routechain.v2.integration.HttpGreedRlClient;
import com.routechain.v2.integration.HttpOpenMeteoClient;
import com.routechain.v2.integration.HttpRouteFinderClient;
import com.routechain.v2.integration.HttpTabularScoringClient;
import com.routechain.v2.integration.HttpTomTomTrafficRefineClient;
import com.routechain.v2.integration.NoOpForecastClient;
import com.routechain.v2.integration.NoOpGreedRlClient;
import com.routechain.v2.integration.NoOpOpenMeteoClient;
import com.routechain.v2.integration.NoOpRouteFinderClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.NoOpTomTomTrafficRefineClient;
import com.routechain.v2.integration.OpenMeteoClient;
import com.routechain.v2.integration.RouteFinderClient;
import com.routechain.v2.integration.TabularScoringClient;
import com.routechain.v2.integration.TestForecastClient;
import com.routechain.v2.integration.TestGreedRlClient;
import com.routechain.v2.integration.TestOpenMeteoClient;
import com.routechain.v2.integration.TestRouteFinderClient;
import com.routechain.v2.integration.TestTabularScoringClient;
import com.routechain.v2.integration.TestTomTomTrafficRefineClient;
import com.routechain.v2.integration.TomTomTrafficRefineClient;
import com.routechain.v2.perf.DispatchPerfBenchmarkHarness;
import com.routechain.v2.perf.DispatchPerfMachineProfile;
import com.routechain.v2.perf.DispatchPerfWorkloadFactory;
import com.routechain.v2.route.RouteProposalSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DispatchQualityBenchmarkHarness {
    private static final Path MODEL_MANIFEST_PATH = Path.of("services", "models", "model-manifest.yaml");

    public DispatchQualityBenchmarkRun benchmark(BenchmarkRequest request) {
        List<DispatchQualityBenchmarkResult> results = request.baselines().stream()
                .map(baseline -> request.workloadSize() == DispatchPerfBenchmarkHarness.WorkloadSize.XL && !request.runDeferredXl()
                        ? deferredResult(request, baseline, "deferred-on-current-machine")
                        : runScenario(request, baseline))
                .toList();
        DispatchQualityComparisonReport comparisonReport = request.baselines().containsAll(List.of(
                DispatchPerfBenchmarkHarness.BaselineId.A,
                DispatchPerfBenchmarkHarness.BaselineId.B,
                DispatchPerfBenchmarkHarness.BaselineId.C))
                ? comparisonReport(request, results)
                : null;
        return new DispatchQualityBenchmarkRun(results, comparisonReport);
    }

    public DispatchAblationResult ablate(AblationRequest request) {
        if (request.workloadSize() == DispatchPerfBenchmarkHarness.WorkloadSize.XL && !request.runDeferredXl()) {
            DispatchQualityMetrics emptyMetrics = emptyMetrics();
            return new DispatchAblationResult(
                    "dispatch-ablation-result/v1",
                    request.scenarioPack().wireName(),
                    request.scenarioPack().wireName(),
                    request.workloadSize().name(),
                    request.executionMode().wireName(),
                    request.component().wireName(),
                    Map.of("deferred", "false"),
                    Map.of("deferred", "true"),
                    emptyMetrics,
                    emptyMetrics,
                    List.of("deferred-on-current-machine"));
        }

        DispatchV2Result controlResult = executeAblationRun(request, true);
        DispatchV2Result variantResult = executeAblationRun(request, false);
        DispatchQualityMetrics controlMetrics = metricsFrom(controlResult);
        DispatchQualityMetrics variantMetrics = metricsFrom(variantResult);
        return new DispatchAblationResult(
                "dispatch-ablation-result/v1",
                request.scenarioPack().wireName(),
                request.scenarioPack().wireName(),
                request.workloadSize().name(),
                request.executionMode().wireName(),
                request.component().wireName(),
                controlConfig(request.component(), true),
                controlConfig(request.component(), false),
                controlMetrics,
                variantMetrics,
                deltaSummary(controlMetrics, variantMetrics));
    }

    private DispatchQualityBenchmarkResult runScenario(BenchmarkRequest request, DispatchPerfBenchmarkHarness.BaselineId baselineId) {
        DispatchV2Result result = executeDispatch(request, baselineId);
        List<String> notes = new ArrayList<>();
        if (!request.authorityRun() && request.executionMode() == ExecutionMode.LOCAL_REAL) {
            notes.add("non-authoritative-local-real-run");
        }
        String runAuthorityClass = request.authorityRun() ? "AUTHORITY_REAL" : "LOCAL_NON_AUTHORITY";
        boolean authorityEligible = request.authorityRun() && notes.stream().noneMatch("non-authoritative-local-real-run"::equals);
        return new DispatchQualityBenchmarkResult(
                "dispatch-quality-benchmark-result/v1",
                Instant.now(),
                gitCommit(),
                DispatchPerfMachineProfile.capture(request.machineLabel()),
                request.executionMode().wireName(),
                runAuthorityClass,
                request.authorityRun(),
                authorityEligible,
                false,
                baselineId.name(),
                request.scenarioPack().wireName(),
                request.scenarioPack().wireName(),
                request.workloadSize().name(),
                traceFamilyId(request, baselineId),
                result.decisionStages(),
                false,
                metricsFrom(result),
                distinctDegrades(result),
                distinctSources(result.mlStageMetadata().stream()
                        .filter(MlStageMetadata::applied)
                        .map(MlStageMetadata::sourceModel)
                        .toList()),
                distinctSources(result.liveStageMetadata().stream()
                        .filter(LiveStageMetadata::applied)
                        .map(LiveStageMetadata::sourceName)
                        .toList()),
                List.copyOf(notes));
    }

    private DispatchQualityBenchmarkResult deferredResult(BenchmarkRequest request,
                                                         DispatchPerfBenchmarkHarness.BaselineId baselineId,
                                                         String note) {
        return new DispatchQualityBenchmarkResult(
                "dispatch-quality-benchmark-result/v1",
                Instant.now(),
                gitCommit(),
                DispatchPerfMachineProfile.capture(request.machineLabel()),
                request.executionMode().wireName(),
                request.authorityRun() ? "AUTHORITY_REAL" : "LOCAL_NON_AUTHORITY",
                request.authorityRun(),
                false,
                false,
                baselineId.name(),
                request.scenarioPack().wireName(),
                request.scenarioPack().wireName(),
                request.workloadSize().name(),
                traceFamilyId(request, baselineId),
                List.of(),
                true,
                emptyMetrics(),
                List.of(),
                List.of(),
                List.of(),
                List.of(note));
    }

    private DispatchV2Result executeDispatch(BenchmarkRequest request, DispatchPerfBenchmarkHarness.BaselineId baselineId) {
        ScenarioDefinition scenario = scenarioDefinition(request.scenarioPack());
        RouteChainDispatchV2Properties properties = baseProperties(baselineId, request.executionMode(), feedbackDirectory(request, baselineId));
        scenario.configureProperties(properties, baselineId);
        ScenarioDependencies dependencies = request.executionMode() == ExecutionMode.CONTROLLED
                ? scenario.controlledDependencies(baselineId)
                : scenario.localRealDependencies(baselineId, properties);
        DispatchV2Request dispatchRequest = scenario.request(
                request.workloadSize(),
                traceFamilyId(request, baselineId),
                baselineId);
        TestDispatchV2Factory.TestDispatchRuntimeHarness harness = TestDispatchV2Factory.harness(
                properties,
                dependencies.tabularScoringClient(),
                dependencies.routeFinderClient(),
                dependencies.greedRlClient(),
                dependencies.forecastClient(),
                dependencies.openMeteoClient(),
                dependencies.tomTomTrafficRefineClient());
        return harness.core().dispatch(dispatchRequest);
    }

    private DispatchV2Result executeAblationRun(AblationRequest request, boolean control) {
        ScenarioDefinition scenario = scenarioDefinition(request.scenarioPack());
        RouteChainDispatchV2Properties properties = baseProperties(
                DispatchPerfBenchmarkHarness.BaselineId.C,
                request.executionMode(),
                feedbackDirectory(request, control));
        scenario.configureProperties(properties, DispatchPerfBenchmarkHarness.BaselineId.C);
        ScenarioDependencies dependencies = request.executionMode() == ExecutionMode.CONTROLLED
                ? scenario.controlledDependencies(DispatchPerfBenchmarkHarness.BaselineId.C)
                : scenario.localRealDependencies(DispatchPerfBenchmarkHarness.BaselineId.C, properties);
        if (!control) {
            dependencies = applyAblation(request.component(), properties, dependencies);
        } else if (request.component() == AblationComponent.HOT_START) {
            properties.getFeedback().setStorageMode(FeedbackStorageMode.FILE);
            properties.getFeedback().setBaseDir(feedbackDirectory(request, true).toString());
            properties.setHotStartEnabled(true);
            properties.getWarmHotStart().setLoadLatestSnapshotOnBoot(true);
        }

        DispatchV2Request requestPayload = scenario.request(
                request.workloadSize(),
                "ablation-%s-%s-%s".formatted(request.component().wireName(), control ? "control" : "variant", request.scenarioPack().wireName()),
                DispatchPerfBenchmarkHarness.BaselineId.C);
        if (request.component() == AblationComponent.HOT_START) {
            return executeHotStartAblation(properties, dependencies, requestPayload, control);
        }
        TestDispatchV2Factory.TestDispatchRuntimeHarness harness = TestDispatchV2Factory.harness(
                properties,
                dependencies.tabularScoringClient(),
                dependencies.routeFinderClient(),
                dependencies.greedRlClient(),
                dependencies.forecastClient(),
                dependencies.openMeteoClient(),
                dependencies.tomTomTrafficRefineClient());
        return harness.core().dispatch(requestPayload);
    }

    private DispatchV2Result executeHotStartAblation(RouteChainDispatchV2Properties properties,
                                                     ScenarioDependencies dependencies,
                                                     DispatchV2Request requestPayload,
                                                     boolean control) {
        if (!control) {
            properties.setHotStartEnabled(false);
            properties.getFeedback().setStorageMode(FeedbackStorageMode.IN_MEMORY);
            TestDispatchV2Factory.TestDispatchRuntimeHarness harness = TestDispatchV2Factory.harness(
                    properties,
                    dependencies.tabularScoringClient(),
                    dependencies.routeFinderClient(),
                    dependencies.greedRlClient(),
                    dependencies.forecastClient(),
                    dependencies.openMeteoClient(),
                    dependencies.tomTomTrafficRefineClient());
            return harness.core().dispatch(requestPayload);
        }

        TestDispatchV2Factory.TestDispatchRuntimeHarness seedHarness = TestDispatchV2Factory.harness(
                properties,
                dependencies.tabularScoringClient(),
                dependencies.routeFinderClient(),
                dependencies.greedRlClient(),
                dependencies.forecastClient(),
                dependencies.openMeteoClient(),
                dependencies.tomTomTrafficRefineClient());
        seedHarness.core().dispatch(DispatchHotStartCertificationHarness.copyWithTraceId(requestPayload, requestPayload.traceId() + "-seed"));

        TestDispatchV2Factory.TestDispatchRuntimeHarness hotHarness = TestDispatchV2Factory.harness(
                properties,
                dependencies.tabularScoringClient(),
                dependencies.routeFinderClient(),
                dependencies.greedRlClient(),
                dependencies.forecastClient(),
                dependencies.openMeteoClient(),
                dependencies.tomTomTrafficRefineClient());
        hotHarness.core().dispatch(DispatchHotStartCertificationHarness.copyWithTraceId(requestPayload, requestPayload.traceId() + "-warm"));
        return hotHarness.core().dispatch(DispatchHotStartCertificationHarness.copyWithTraceId(requestPayload, requestPayload.traceId() + "-hot"));
    }

    private ScenarioDependencies applyAblation(AblationComponent component,
                                               RouteChainDispatchV2Properties properties,
                                               ScenarioDependencies dependencies) {
        return switch (component) {
            case TABULAR -> {
                properties.getMl().getTabular().setEnabled(false);
                yield dependencies.withTabularClient(new NoOpTabularScoringClient());
            }
            case ROUTEFINDER -> {
                properties.getMl().getRoutefinder().setEnabled(false);
                yield dependencies.withRouteFinderClient(new NoOpRouteFinderClient());
            }
            case GREEDRL -> {
                properties.getMl().getGreedrl().setEnabled(false);
                yield dependencies.withGreedRlClient(new NoOpGreedRlClient());
            }
            case FORECAST -> {
                properties.getMl().getForecast().setEnabled(false);
                yield dependencies.withForecastClient(new NoOpForecastClient());
            }
            case TOMTOM -> {
                properties.setTomtomEnabled(false);
                properties.getTraffic().setEnabled(false);
                yield dependencies.withTomTomTrafficRefineClient(new NoOpTomTomTrafficRefineClient());
            }
            case OPEN_METEO -> {
                properties.setOpenMeteoEnabled(false);
                properties.getWeather().setEnabled(false);
                yield dependencies.withOpenMeteoClient(new NoOpOpenMeteoClient());
            }
            case ORTOOLS -> {
                properties.setSelectorOrtoolsEnabled(false);
                yield dependencies;
            }
            case HOT_START -> {
                properties.setHotStartEnabled(false);
                properties.getFeedback().setStorageMode(FeedbackStorageMode.IN_MEMORY);
                yield dependencies;
            }
        };
    }

    private Map<String, String> controlConfig(AblationComponent component, boolean control) {
        return switch (component) {
            case TABULAR -> Map.of("tabular", control ? "on" : "off");
            case ROUTEFINDER -> Map.of("routefinder", control ? "on" : "off");
            case GREEDRL -> Map.of("greedrl", control ? "on" : "off");
            case FORECAST -> Map.of("forecast", control ? "on" : "off");
            case TOMTOM -> Map.of("tomtom", control ? "on" : "off");
            case OPEN_METEO -> Map.of("open-meteo", control ? "on" : "off");
            case ORTOOLS -> Map.of("selector-mode", control ? "ortools" : "degraded-greedy");
            case HOT_START -> Map.of("hot-start", control ? "on" : "off");
        };
    }

    private DispatchQualityComparisonReport comparisonReport(BenchmarkRequest request, List<DispatchQualityBenchmarkResult> results) {
        Map<String, DispatchQualityBenchmarkResult> byBaseline = new LinkedHashMap<>();
        for (DispatchQualityBenchmarkResult result : results) {
            byBaseline.put(result.baselineId(), result);
        }
        DispatchQualityBenchmarkResult baselineA = byBaseline.get("A");
        DispatchQualityBenchmarkResult baselineB = byBaseline.get("B");
        DispatchQualityBenchmarkResult baselineC = byBaseline.get("C");
        List<String> advantages = new ArrayList<>();
        List<String> regressions = new ArrayList<>();
        if (baselineC != null && baselineA != null) {
            compareAgainst("A", baselineC.metrics(), baselineA.metrics(), advantages, regressions);
        }
        if (baselineC != null && baselineB != null) {
            compareAgainst("B", baselineC.metrics(), baselineB.metrics(), advantages, regressions);
        }
        String summary = "Full V2 has %d advantages and %d regressions against selected baselines".formatted(
                advantages.size(),
                regressions.size());
        return new DispatchQualityComparisonReport(
                "dispatch-quality-comparison-report/v1",
                request.scenarioPack().wireName(),
                request.scenarioPack().wireName(),
                request.workloadSize().name(),
                request.executionMode().wireName(),
                request.authorityRun() ? "AUTHORITY_REAL" : "LOCAL_NON_AUTHORITY",
                request.authorityRun(),
                results.stream().allMatch(DispatchQualityBenchmarkResult::authorityEligible),
                false,
                List.copyOf(results),
                List.copyOf(advantages),
                List.copyOf(regressions),
                summary);
    }

    private void compareAgainst(String baselineName,
                                DispatchQualityMetrics full,
                                DispatchQualityMetrics baseline,
                                List<String> advantages,
                                List<String> regressions) {
        compareHigherBetter("selectedProposalCount", full.selectedProposalCount(), baseline.selectedProposalCount(), baselineName, advantages, regressions);
        compareHigherBetter("executedAssignmentCount", full.executedAssignmentCount(), baseline.executedAssignmentCount(), baselineName, advantages, regressions);
        compareHigherBetter("bundleRate", full.bundleRate(), baseline.bundleRate(), baselineName, advantages, regressions);
        compareHigherBetter("averageBundleSize", full.averageBundleSize(), baseline.averageBundleSize(), baselineName, advantages, regressions);
        compareLowerBetter("routeFallbackRate", full.routeFallbackRate(), baseline.routeFallbackRate(), baselineName, advantages, regressions);
        compareLowerBetter("averageProjectedPickupEtaMinutes", full.averageProjectedPickupEtaMinutes(), baseline.averageProjectedPickupEtaMinutes(), baselineName, advantages, regressions);
        compareLowerBetter("averageProjectedCompletionEtaMinutes", full.averageProjectedCompletionEtaMinutes(), baseline.averageProjectedCompletionEtaMinutes(), baselineName, advantages, regressions);
        compareHigherBetter("landingValueAverage", full.landingValueAverage(), baseline.landingValueAverage(), baselineName, advantages, regressions);
        compareHigherBetter("robustUtilityAverage", full.robustUtilityAverage(), baseline.robustUtilityAverage(), baselineName, advantages, regressions);
        compareHigherBetter("selectorObjectiveValue", full.selectorObjectiveValue(), baseline.selectorObjectiveValue(), baselineName, advantages, regressions);
        compareLowerBetter("degradeRate", full.degradeRate(), baseline.degradeRate(), baselineName, advantages, regressions);
        compareLowerBetter("workerFallbackRate", full.workerFallbackRate(), baseline.workerFallbackRate(), baselineName, advantages, regressions);
        compareLowerBetter("liveSourceFallbackRate", full.liveSourceFallbackRate(), baseline.liveSourceFallbackRate(), baselineName, advantages, regressions);
        if (full.conflictFreeAssignments() && !baseline.conflictFreeAssignments()) {
            advantages.add("conflictFreeAssignments better than %s".formatted(baselineName));
        } else if (!full.conflictFreeAssignments() && baseline.conflictFreeAssignments()) {
            regressions.add("conflictFreeAssignments worse than %s".formatted(baselineName));
        }
    }

    private void compareHigherBetter(String metric,
                                     double fullValue,
                                     double baselineValue,
                                     String baselineName,
                                     List<String> advantages,
                                     List<String> regressions) {
        if (Double.compare(fullValue, baselineValue) > 0) {
            advantages.add("%s better than %s (%s > %s)".formatted(metric, baselineName, fullValue, baselineValue));
        } else if (Double.compare(fullValue, baselineValue) < 0) {
            regressions.add("%s worse than %s (%s < %s)".formatted(metric, baselineName, fullValue, baselineValue));
        }
    }

    private void compareLowerBetter(String metric,
                                    double fullValue,
                                    double baselineValue,
                                    String baselineName,
                                    List<String> advantages,
                                    List<String> regressions) {
        if (Double.compare(fullValue, baselineValue) < 0) {
            advantages.add("%s better than %s (%s < %s)".formatted(metric, baselineName, fullValue, baselineValue));
        } else if (Double.compare(fullValue, baselineValue) > 0) {
            regressions.add("%s worse than %s (%s > %s)".formatted(metric, baselineName, fullValue, baselineValue));
        }
    }

    private List<String> deltaSummary(DispatchQualityMetrics control, DispatchQualityMetrics variant) {
        List<String> lines = new ArrayList<>();
        lines.add("selectedProposalCount delta=" + (variant.selectedProposalCount() - control.selectedProposalCount()));
        lines.add("executedAssignmentCount delta=" + (variant.executedAssignmentCount() - control.executedAssignmentCount()));
        lines.add("bundleRate delta=" + (variant.bundleRate() - control.bundleRate()));
        lines.add("robustUtilityAverage delta=" + (variant.robustUtilityAverage() - control.robustUtilityAverage()));
        lines.add("selectorObjectiveValue delta=" + (variant.selectorObjectiveValue() - control.selectorObjectiveValue()));
        return List.copyOf(lines);
    }

    private DispatchQualityMetrics metricsFrom(DispatchV2Result result) {
        int executedAssignmentCount = result.dispatchExecutionSummary().executedAssignmentCount();
        long bundledAssignments = result.assignments().stream()
                .filter(assignment -> assignment.orderIds().size() > 1)
                .count();
        double averageBundleSize = result.assignments().stream()
                .filter(assignment -> assignment.orderIds().size() > 1)
                .mapToInt(assignment -> assignment.orderIds().size())
                .average()
                .orElse(0.0);
        double averagePickupEta = result.assignments().stream()
                .mapToDouble(assignment -> assignment.projectedPickupEtaMinutes())
                .average()
                .orElse(0.0);
        double averageCompletionEta = result.assignments().stream()
                .mapToDouble(assignment -> assignment.projectedCompletionEtaMinutes())
                .average()
                .orElse(0.0);
        double landingValueAverage = result.robustUtilities().stream()
                .mapToDouble(robustUtility -> robustUtility.landingValue())
                .average()
                .orElse(0.0);
        double robustUtilityAverage = result.robustUtilities().stream()
                .mapToDouble(robustUtility -> robustUtility.robustUtility())
                .average()
                .orElse(0.0);
        return new DispatchQualityMetrics(
                "dispatch-quality-metrics/v1",
                result.globalSelectionResult().selectedCount(),
                executedAssignmentCount,
                conflictFreeAssignments(result),
                executedAssignmentCount == 0 ? 0.0 : bundledAssignments / (double) executedAssignmentCount,
                averageBundleSize,
                routeFallbackRate(result),
                averagePickupEta,
                averageCompletionEta,
                landingValueAverage,
                robustUtilityAverage,
                result.globalSelectionResult().objectiveValue(),
                distinctDegrades(result).isEmpty() ? 0.0 : 1.0,
                fallbackRate(result.mlStageMetadata().stream().map(MlStageMetadata::fallbackUsed).toList()),
                fallbackRate(result.liveStageMetadata().stream().map(LiveStageMetadata::fallbackUsed).toList()));
    }

    private double routeFallbackRate(DispatchV2Result result) {
        int executedAssignmentCount = result.dispatchExecutionSummary().executedAssignmentCount();
        long fallbackAssignments = result.assignments().stream()
                .filter(assignment -> assignment.routeSource() == RouteProposalSource.FALLBACK_SIMPLE
                        || containsFallbackSignal(assignment.reasons())
                        || containsFallbackSignal(assignment.degradeReasons()))
                .count();
        if (executedAssignmentCount > 0) {
            return fallbackAssignments / (double) executedAssignmentCount;
        }
        Integer fallbackProposals = result.routeProposalSummary().sourceCounts().get(RouteProposalSource.FALLBACK_SIMPLE);
        if (result.routeProposalSummary().proposalCount() <= 0 || fallbackProposals == null) {
            return 0.0;
        }
        return fallbackProposals / (double) result.routeProposalSummary().proposalCount();
    }

    private boolean containsFallbackSignal(List<String> reasons) {
        return reasons.stream()
                .filter(reason -> reason != null)
                .map(reason -> reason.toLowerCase(Locale.ROOT))
                .anyMatch(reason -> reason.contains("fallback"));
    }

    private double fallbackRate(List<Boolean> fallbackFlags) {
        if (fallbackFlags.isEmpty()) {
            return 0.0;
        }
        long fallbacks = fallbackFlags.stream().filter(Boolean::booleanValue).count();
        return fallbacks / (double) fallbackFlags.size();
    }

    private boolean conflictFreeAssignments(DispatchV2Result result) {
        Set<String> seenDrivers = new LinkedHashSet<>();
        Set<String> seenOrders = new LinkedHashSet<>();
        for (var assignment : result.assignments()) {
            if (!seenDrivers.add(assignment.driverId())) {
                return false;
            }
            for (String orderId : assignment.orderIds()) {
                if (!seenOrders.add(orderId)) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<String> distinctDegrades(DispatchV2Result result) {
        return distinctSources(java.util.stream.Stream.of(
                        result.degradeReasons().stream(),
                        result.liveStageMetadata().stream()
                                .map(LiveStageMetadata::degradeReason)
                                .filter(reason -> reason != null && !reason.isBlank()),
                        result.routeProposalSummary().degradeReasons().stream(),
                        result.scenarioEvaluationSummary().degradeReasons().stream(),
                        result.globalSelectionResult().degradeReasons().stream(),
                        result.dispatchExecutionSummary().degradeReasons().stream())
                .flatMap(stream -> stream)
                .toList());
    }

    private List<String> distinctSources(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private DispatchQualityMetrics emptyMetrics() {
        return new DispatchQualityMetrics(
                "dispatch-quality-metrics/v1",
                0,
                0,
                true,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0);
    }

    private RouteChainDispatchV2Properties baseProperties(DispatchPerfBenchmarkHarness.BaselineId baselineId,
                                                          ExecutionMode executionMode,
                                                          Path feedbackDirectory) {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setEnabled(true);
        properties.setMlEnabled(false);
        properties.setSelectorOrtoolsEnabled(false);
        properties.setHotStartEnabled(false);
        properties.setTomtomEnabled(false);
        properties.setOpenMeteoEnabled(false);
        properties.getWeather().setEnabled(false);
        properties.getTraffic().setEnabled(false);
        properties.getMl().getTabular().setEnabled(false);
        properties.getMl().getRoutefinder().setEnabled(false);
        properties.getMl().getGreedrl().setEnabled(false);
        properties.getMl().getForecast().setEnabled(false);
        if (feedbackDirectory != null && executionMode == ExecutionMode.LOCAL_REAL) {
            properties.getFeedback().setStorageMode(FeedbackStorageMode.FILE);
            properties.getFeedback().setBaseDir(feedbackDirectory.toString());
            properties.getWarmHotStart().setLoadLatestSnapshotOnBoot(true);
        } else {
            properties.getFeedback().setStorageMode(FeedbackStorageMode.IN_MEMORY);
        }
        if (baselineId == DispatchPerfBenchmarkHarness.BaselineId.B || baselineId == DispatchPerfBenchmarkHarness.BaselineId.C) {
            properties.setSelectorOrtoolsEnabled(true);
        }
        if (baselineId == DispatchPerfBenchmarkHarness.BaselineId.C) {
            properties.setMlEnabled(true);
            properties.setHotStartEnabled(true);
            properties.getMl().getTabular().setEnabled(true);
            properties.getMl().getRoutefinder().setEnabled(true);
            properties.getMl().getGreedrl().setEnabled(true);
            properties.getMl().getForecast().setEnabled(true);
        }
        return properties;
    }

    private Path feedbackDirectory(BenchmarkRequest request, DispatchPerfBenchmarkHarness.BaselineId baselineId) {
        return request.outputRoot()
                .resolve("feedback")
                .resolve(request.scenarioPack().wireName())
                .resolve(request.workloadSize().name().toLowerCase(Locale.ROOT))
                .resolve(request.executionMode().wireName())
                .resolve(baselineId.name().toLowerCase(Locale.ROOT));
    }

    private Path feedbackDirectory(AblationRequest request, boolean control) {
        return request.outputRoot()
                .resolve("feedback")
                .resolve("ablation")
                .resolve(request.component().wireName())
                .resolve(request.scenarioPack().wireName())
                .resolve(control ? "control" : "variant");
    }

    private String traceFamilyId(BenchmarkRequest request, DispatchPerfBenchmarkHarness.BaselineId baselineId) {
        return "quality-%s-%s-%s".formatted(
                request.scenarioPack().wireName(),
                request.workloadSize().name().toLowerCase(Locale.ROOT),
                baselineId.name().toLowerCase(Locale.ROOT));
    }

    private ScenarioDefinition scenarioDefinition(ScenarioPack scenarioPack) {
        return switch (scenarioPack) {
            case NORMAL_CLEAR -> new ScenarioDefinition() {
                @Override
                public void configureProperties(RouteChainDispatchV2Properties properties, DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    if (baselineId == DispatchPerfBenchmarkHarness.BaselineId.C) {
                        properties.getMl().getForecast().setEnabled(true);
                    }
                }

                @Override
                public DispatchV2Request request(DispatchPerfBenchmarkHarness.WorkloadSize workloadSize, String traceId, DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    return DispatchPerfWorkloadFactory.request(workloadSize, traceId);
                }

                @Override
                public ScenarioDependencies controlledDependencies(DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    return baselineId == DispatchPerfBenchmarkHarness.BaselineId.C ? appliedMlOnly() : noOps();
                }
            };
            case HEAVY_RAIN -> new ScenarioDefinition() {
                @Override
                public void configureProperties(RouteChainDispatchV2Properties properties, DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    if (baselineId != DispatchPerfBenchmarkHarness.BaselineId.A) {
                        properties.setOpenMeteoEnabled(true);
                        properties.getWeather().setEnabled(true);
                    }
                }

                @Override
                public DispatchV2Request request(DispatchPerfBenchmarkHarness.WorkloadSize workloadSize, String traceId, DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    DispatchV2Request request = DispatchPerfWorkloadFactory.request(workloadSize, traceId);
                    return new DispatchV2Request(
                            request.schemaVersion(),
                            traceId,
                            request.openOrders(),
                            request.availableDrivers(),
                            request.regions(),
                            WeatherProfile.HEAVY_RAIN,
                            request.decisionTime());
                }

                @Override
                public ScenarioDependencies controlledDependencies(DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    if (baselineId == DispatchPerfBenchmarkHarness.BaselineId.C) {
                        return appliedMlOnly().withOpenMeteoClient(TestOpenMeteoClient.freshHeavyRain());
                    }
                    if (baselineId == DispatchPerfBenchmarkHarness.BaselineId.B) {
                        return noOps().withOpenMeteoClient(TestOpenMeteoClient.freshHeavyRain());
                    }
                    return noOps();
                }
            };
            case TRAFFIC_SHOCK -> new ScenarioDefinition() {
                @Override
                public void configureProperties(RouteChainDispatchV2Properties properties, DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    if (baselineId != DispatchPerfBenchmarkHarness.BaselineId.A) {
                        properties.setTomtomEnabled(true);
                        properties.getTraffic().setEnabled(true);
                    }
                }

                @Override
                public DispatchV2Request request(DispatchPerfBenchmarkHarness.WorkloadSize workloadSize, String traceId, DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    DispatchV2Request request = DispatchPerfWorkloadFactory.request(workloadSize, traceId);
                    return DispatchHotStartCertificationHarness.copyWithDecisionTime(
                            request,
                            traceId,
                            Instant.parse("2026-04-16T08:00:00Z"));
                }

                @Override
                public ScenarioDependencies controlledDependencies(DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    if (baselineId == DispatchPerfBenchmarkHarness.BaselineId.C) {
                        return appliedMlOnly().withTomTomTrafficRefineClient(TestTomTomTrafficRefineClient.applied(1.35, true));
                    }
                    if (baselineId == DispatchPerfBenchmarkHarness.BaselineId.B) {
                        return noOps().withTomTomTrafficRefineClient(TestTomTomTrafficRefineClient.applied(1.35, true));
                    }
                    return noOps();
                }
            };
            case FORECAST_HEAVY -> new ScenarioDefinition() {
                @Override
                public void configureProperties(RouteChainDispatchV2Properties properties, DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    if (baselineId == DispatchPerfBenchmarkHarness.BaselineId.C) {
                        properties.getMl().getForecast().setEnabled(true);
                    }
                }

                @Override
                public DispatchV2Request request(DispatchPerfBenchmarkHarness.WorkloadSize workloadSize, String traceId, DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    return DispatchPerfWorkloadFactory.request(workloadSize, traceId);
                }

                @Override
                public ScenarioDependencies controlledDependencies(DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    if (baselineId == DispatchPerfBenchmarkHarness.BaselineId.C) {
                        return appliedMlOnly().withForecastClient(TestForecastClient.applied());
                    }
                    return noOps();
                }
            };
            case WORKER_DEGRADATION -> new ScenarioDefinition() {
                @Override
                public void configureProperties(RouteChainDispatchV2Properties properties, DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    if (baselineId == DispatchPerfBenchmarkHarness.BaselineId.C) {
                        properties.getMl().getForecast().setEnabled(true);
                    }
                }

                @Override
                public DispatchV2Request request(DispatchPerfBenchmarkHarness.WorkloadSize workloadSize, String traceId, DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    return DispatchPerfWorkloadFactory.request(workloadSize, traceId);
                }

                @Override
                public ScenarioDependencies controlledDependencies(DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    if (baselineId == DispatchPerfBenchmarkHarness.BaselineId.C) {
                        return appliedMlOnly().withTabularClient(TestTabularScoringClient.notApplied("tabular-unavailable"));
                    }
                    return noOps();
                }
            };
            case LIVE_SOURCE_DEGRADATION -> new ScenarioDefinition() {
                @Override
                public void configureProperties(RouteChainDispatchV2Properties properties, DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    if (baselineId != DispatchPerfBenchmarkHarness.BaselineId.A) {
                        properties.setOpenMeteoEnabled(true);
                        properties.getWeather().setEnabled(true);
                        properties.setTomtomEnabled(true);
                        properties.getTraffic().setEnabled(true);
                    }
                }

                @Override
                public DispatchV2Request request(DispatchPerfBenchmarkHarness.WorkloadSize workloadSize, String traceId, DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    return DispatchPerfWorkloadFactory.request(workloadSize, traceId);
                }

                @Override
                public ScenarioDependencies controlledDependencies(DispatchPerfBenchmarkHarness.BaselineId baselineId) {
                    if (baselineId == DispatchPerfBenchmarkHarness.BaselineId.C) {
                        return appliedMlOnly()
                                .withOpenMeteoClient(TestOpenMeteoClient.unavailable("open-meteo-unavailable"))
                                .withTomTomTrafficRefineClient(TestTomTomTrafficRefineClient.unavailable("tomtom-unavailable"));
                    }
                    if (baselineId == DispatchPerfBenchmarkHarness.BaselineId.B) {
                        return noOps()
                                .withOpenMeteoClient(TestOpenMeteoClient.unavailable("open-meteo-unavailable"))
                                .withTomTomTrafficRefineClient(TestTomTomTrafficRefineClient.unavailable("tomtom-unavailable"));
                    }
                    return noOps();
                }
            };
        };
    }

    private static ScenarioDependencies noOps() {
        return new ScenarioDependencies(
                new NoOpTabularScoringClient(),
                new NoOpRouteFinderClient(),
                new NoOpGreedRlClient(),
                new NoOpForecastClient(),
                new NoOpOpenMeteoClient(),
                new NoOpTomTomTrafficRefineClient());
    }

    private static ScenarioDependencies appliedMlOnly() {
        return new ScenarioDependencies(
                TestTabularScoringClient.applied(0.15),
                TestRouteFinderClient.applied(),
                TestGreedRlClient.applied(),
                TestForecastClient.applied(),
                new NoOpOpenMeteoClient(),
                new NoOpTomTomTrafficRefineClient());
    }

    private static ScenarioDependencies localRealDependencies(RouteChainDispatchV2Properties properties) {
        if (!Files.exists(MODEL_MANIFEST_PATH)) {
            return noOps();
        }
        String apiKey = System.getenv("TOMTOM_API_KEY");
        return new ScenarioDependencies(
                new HttpTabularScoringClient(
                        properties.getMl().getTabular().getBaseUrl(),
                        properties.getMl().getTabular().getConnectTimeout(),
                        properties.getMl().getTabular().getReadTimeout(),
                        MODEL_MANIFEST_PATH),
                new HttpRouteFinderClient(
                        properties.getMl().getRoutefinder().getBaseUrl(),
                        properties.getMl().getRoutefinder().getConnectTimeout(),
                        properties.getMl().getRoutefinder().getReadTimeout(),
                        MODEL_MANIFEST_PATH),
                new HttpGreedRlClient(
                        properties.getMl().getGreedrl().getBaseUrl(),
                        properties.getMl().getGreedrl().getConnectTimeout(),
                        properties.getMl().getGreedrl().getReadTimeout(),
                        MODEL_MANIFEST_PATH),
                new HttpForecastClient(
                        properties.getMl().getForecast().getBaseUrl(),
                        properties.getMl().getForecast().getConnectTimeout(),
                        properties.getMl().getForecast().getReadTimeout(),
                        MODEL_MANIFEST_PATH),
                new HttpOpenMeteoClient(
                        properties.getWeather().getBaseUrl(),
                        properties.getWeather().getConnectTimeout(),
                        properties.getWeather().getReadTimeout(),
                        properties),
                new HttpTomTomTrafficRefineClient(
                        properties.getTraffic().getBaseUrl(),
                        (apiKey == null || apiKey.isBlank()) ? properties.getTraffic().getApiKey() : apiKey,
                        properties.getTraffic().getConnectTimeout(),
                        properties.getTraffic().getReadTimeout(),
                        new TrafficRefineMapper()));
    }

    private String gitCommit() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                int exit = process.waitFor();
                if (exit == 0 && line != null && !line.isBlank()) {
                    return line.trim();
                }
            }
        } catch (IOException exception) {
            return "workspace";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "workspace";
        }
        return "workspace";
    }

    public record BenchmarkRequest(
            List<DispatchPerfBenchmarkHarness.BaselineId> baselines,
            DispatchPerfBenchmarkHarness.WorkloadSize workloadSize,
            ScenarioPack scenarioPack,
            ExecutionMode executionMode,
            String machineLabel,
            boolean authorityRun,
            boolean runDeferredXl,
            Path outputRoot) {
    }

    public record AblationRequest(
            AblationComponent component,
            DispatchPerfBenchmarkHarness.WorkloadSize workloadSize,
            ScenarioPack scenarioPack,
            ExecutionMode executionMode,
            boolean runDeferredXl,
            Path outputRoot) {
    }

    public enum ExecutionMode {
        CONTROLLED("controlled"),
        LOCAL_REAL("local-real");

        private final String wireName;

        ExecutionMode(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }
    }

    public enum ScenarioPack {
        NORMAL_CLEAR("normal-clear"),
        HEAVY_RAIN("heavy-rain"),
        TRAFFIC_SHOCK("traffic-shock"),
        FORECAST_HEAVY("forecast-heavy"),
        WORKER_DEGRADATION("worker-degradation"),
        LIVE_SOURCE_DEGRADATION("live-source-degradation");

        private final String wireName;

        ScenarioPack(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static ScenarioPack fromWire(String value) {
            return EnumSet.allOf(ScenarioPack.class).stream()
                    .filter(pack -> pack.wireName.equals(value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown scenario pack: " + value));
        }
    }

    public enum AblationComponent {
        TABULAR("tabular"),
        ROUTEFINDER("routefinder"),
        GREEDRL("greedrl"),
        FORECAST("forecast"),
        TOMTOM("tomtom"),
        OPEN_METEO("open-meteo"),
        ORTOOLS("ortools"),
        HOT_START("hot-start");

        private final String wireName;

        AblationComponent(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static AblationComponent fromWire(String value) {
            return EnumSet.allOf(AblationComponent.class).stream()
                    .filter(component -> component.wireName.equals(value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown ablation component: " + value));
        }
    }

    private interface ScenarioDefinition {
        void configureProperties(RouteChainDispatchV2Properties properties, DispatchPerfBenchmarkHarness.BaselineId baselineId);

        DispatchV2Request request(DispatchPerfBenchmarkHarness.WorkloadSize workloadSize,
                                  String traceId,
                                  DispatchPerfBenchmarkHarness.BaselineId baselineId);

        ScenarioDependencies controlledDependencies(DispatchPerfBenchmarkHarness.BaselineId baselineId);

        default ScenarioDependencies localRealDependencies(DispatchPerfBenchmarkHarness.BaselineId baselineId,
                                                           RouteChainDispatchV2Properties properties) {
            ScenarioDependencies realDependencies = DispatchQualityBenchmarkHarness.localRealDependencies(properties);
            if (baselineId == DispatchPerfBenchmarkHarness.BaselineId.C) {
                return realDependencies;
            }
            return DispatchQualityBenchmarkHarness.noOps()
                    .withOpenMeteoClient(realDependencies.openMeteoClient())
                    .withTomTomTrafficRefineClient(realDependencies.tomTomTrafficRefineClient());
        }
    }

    private record ScenarioDependencies(
            TabularScoringClient tabularScoringClient,
            RouteFinderClient routeFinderClient,
            GreedRlClient greedRlClient,
            ForecastClient forecastClient,
            OpenMeteoClient openMeteoClient,
            TomTomTrafficRefineClient tomTomTrafficRefineClient) {

        ScenarioDependencies withTabularClient(TabularScoringClient client) {
            return new ScenarioDependencies(client, routeFinderClient, greedRlClient, forecastClient, openMeteoClient, tomTomTrafficRefineClient);
        }

        ScenarioDependencies withRouteFinderClient(RouteFinderClient client) {
            return new ScenarioDependencies(tabularScoringClient, client, greedRlClient, forecastClient, openMeteoClient, tomTomTrafficRefineClient);
        }

        ScenarioDependencies withGreedRlClient(GreedRlClient client) {
            return new ScenarioDependencies(tabularScoringClient, routeFinderClient, client, forecastClient, openMeteoClient, tomTomTrafficRefineClient);
        }

        ScenarioDependencies withForecastClient(ForecastClient client) {
            return new ScenarioDependencies(tabularScoringClient, routeFinderClient, greedRlClient, client, openMeteoClient, tomTomTrafficRefineClient);
        }

        ScenarioDependencies withOpenMeteoClient(OpenMeteoClient client) {
            return new ScenarioDependencies(tabularScoringClient, routeFinderClient, greedRlClient, forecastClient, client, tomTomTrafficRefineClient);
        }

        ScenarioDependencies withTomTomTrafficRefineClient(TomTomTrafficRefineClient client) {
            return new ScenarioDependencies(tabularScoringClient, routeFinderClient, greedRlClient, forecastClient, openMeteoClient, client);
        }
    }
}
