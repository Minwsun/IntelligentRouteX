package com.routechain.v2.chaos;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import com.routechain.v2.certification.DispatchHotStartCertificationHarness;
import com.routechain.v2.feedback.DispatchRuntimeReuseState;
import com.routechain.v2.feedback.DispatchRuntimeSnapshot;
import com.routechain.v2.integration.ForecastResult;
import com.routechain.v2.integration.MlWorkerMetadata;
import com.routechain.v2.integration.TestForecastClient;
import com.routechain.v2.integration.TestOpenMeteoClient;
import com.routechain.v2.integration.TestTomTomTrafficRefineClient;
import com.routechain.v2.integration.WorkerReadyState;
import com.routechain.v2.perf.DispatchPerfBenchmarkHarness;
import com.routechain.v2.perf.DispatchPerfMachineProfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DispatchChaosBenchmarkHarness {

    public DispatchChaosRunResult run(ChaosRequest request) {
        return switch (request.faultType()) {
            case WORKER_MALFORMED_RESPONSE, WORKER_FINGERPRINT_MISMATCH -> deferredResult(request, "deferred-existing-http-contract-only");
            case WARM_BOOT_INVALID_SNAPSHOT -> warmBootInvalidSnapshot(request);
            case REUSE_STATE_LOAD_MISSING_OR_INVALID -> reuseStateInvalid(request);
            case PARTIAL_HOT_START_DRIFT -> partialHotStartDrift(request);
            default -> singleDispatchFault(request);
        };
    }

    public DispatchStabilitySummary summarize(List<DispatchChaosRunResult> results) {
        List<DispatchStabilityScenarioOutcome> outcomes = results.stream()
                .map(result -> new DispatchStabilityScenarioOutcome(
                        "dispatch-stability-scenario-outcome/v1",
                        result.faultType(),
                        result.passed(),
                        result.notes()))
                .toList();
        List<String> failures = results.stream()
                .filter(result -> !result.passed() && !result.deferred())
                .map(result -> "chaos-failed:" + result.faultType())
                .toList();
        long passed = results.stream().filter(DispatchChaosRunResult::passed).count();
        return new DispatchStabilitySummary(
                "dispatch-stability-summary/v1",
                "chaos",
                results.size(),
                (int) passed,
                results.size() - (int) passed,
                outcomes,
                failures);
    }

    private DispatchChaosRunResult singleDispatchFault(ChaosRequest request) {
        DispatchPhase3Support.ScenarioDefinition scenario = DispatchPhase3Support.scenarioDefinition(request.scenarioPack());
        Path feedbackDirectory = feedbackDirectory(request);
        RouteChainDispatchV2Properties properties = DispatchPhase3Support.baseProperties(
                DispatchPerfBenchmarkHarness.BaselineId.C,
                request.executionMode(),
                feedbackDirectory);
        scenario.configureProperties(properties, DispatchPerfBenchmarkHarness.BaselineId.C);
        DispatchPhase3Support.ScenarioDependencies dependencies = faultDependencies(
                request.faultType(),
                scenario.dependencies(request.executionMode(), DispatchPerfBenchmarkHarness.BaselineId.C, properties),
                properties);
        TestDispatchV2Factory.TestDispatchRuntimeHarness harness = DispatchPhase3Support.harness(properties, dependencies);
        DispatchV2Result result = harness.core().dispatch(scenario.request(
                request.workloadSize(),
                traceId(request, "fault"),
                DispatchPerfBenchmarkHarness.BaselineId.C));
        return resultFromDispatch(request, result, List.of(), 1, false);
    }

    private DispatchChaosRunResult warmBootInvalidSnapshot(ChaosRequest request) {
        DispatchPhase3Support.ScenarioDefinition scenario = DispatchPhase3Support.scenarioDefinition(request.scenarioPack());
        Path feedbackDirectory = feedbackDirectory(request);
        RouteChainDispatchV2Properties seedProperties = DispatchPhase3Support.baseProperties(
                DispatchPerfBenchmarkHarness.BaselineId.C,
                request.executionMode(),
                feedbackDirectory);
        scenario.configureProperties(seedProperties, DispatchPerfBenchmarkHarness.BaselineId.C);
        var seedHarness = DispatchPhase3Support.harness(
                seedProperties,
                scenario.dependencies(request.executionMode(), DispatchPerfBenchmarkHarness.BaselineId.C, seedProperties));
        seedHarness.core().dispatch(scenario.request(request.workloadSize(), traceId(request, "seed"), DispatchPerfBenchmarkHarness.BaselineId.C));
        corruptLatestJson(feedbackDirectory.resolve("snapshots"), "dispatch-runtime-snapshot/v1", "dispatch-runtime-snapshot/legacy");

        RouteChainDispatchV2Properties warmProperties = DispatchPhase3Support.baseProperties(
                DispatchPerfBenchmarkHarness.BaselineId.C,
                request.executionMode(),
                feedbackDirectory);
        scenario.configureProperties(warmProperties, DispatchPerfBenchmarkHarness.BaselineId.C);
        var warmHarness = DispatchPhase3Support.harness(
                warmProperties,
                scenario.dependencies(request.executionMode(), DispatchPerfBenchmarkHarness.BaselineId.C, warmProperties));
        DispatchV2Result result = warmHarness.core().dispatch(scenario.request(
                request.workloadSize(),
                traceId(request, "warm-invalid-snapshot"),
                DispatchPerfBenchmarkHarness.BaselineId.C));
        List<String> extraDegrades = warmHarness.warmStartManager().currentState().degradeReasons();
        return resultFromDispatch(request, result, extraDegrades, 1, false);
    }

    private DispatchChaosRunResult reuseStateInvalid(ChaosRequest request) {
        DispatchPhase3Support.ScenarioDefinition scenario = DispatchPhase3Support.scenarioDefinition(request.scenarioPack());
        Path feedbackDirectory = feedbackDirectory(request);
        RouteChainDispatchV2Properties seedProperties = DispatchPhase3Support.baseProperties(
                DispatchPerfBenchmarkHarness.BaselineId.C,
                request.executionMode(),
                feedbackDirectory);
        scenario.configureProperties(seedProperties, DispatchPerfBenchmarkHarness.BaselineId.C);
        var seedHarness = DispatchPhase3Support.harness(
                seedProperties,
                scenario.dependencies(request.executionMode(), DispatchPerfBenchmarkHarness.BaselineId.C, seedProperties));
        seedHarness.core().dispatch(scenario.request(request.workloadSize(), traceId(request, "seed"), DispatchPerfBenchmarkHarness.BaselineId.C));
        corruptLatestJson(feedbackDirectory.resolve("reuse-states"), "dispatch-runtime-reuse-state/v1", "dispatch-runtime-reuse-state/legacy");

        RouteChainDispatchV2Properties warmProperties = DispatchPhase3Support.baseProperties(
                DispatchPerfBenchmarkHarness.BaselineId.C,
                request.executionMode(),
                feedbackDirectory);
        scenario.configureProperties(warmProperties, DispatchPerfBenchmarkHarness.BaselineId.C);
        var warmHarness = DispatchPhase3Support.harness(
                warmProperties,
                scenario.dependencies(request.executionMode(), DispatchPerfBenchmarkHarness.BaselineId.C, warmProperties));
        DispatchV2Result result = warmHarness.core().dispatch(scenario.request(
                request.workloadSize(),
                traceId(request, "invalid-reuse"),
                DispatchPerfBenchmarkHarness.BaselineId.C));
        List<String> extraDegrades = new ArrayList<>(result.hotStartState().degradeReasons());
        extraDegrades.addAll(warmHarness.reuseStateService().loadLatest().degradeReasons());
        return resultFromDispatch(request, result, extraDegrades, 1, false);
    }

    private DispatchChaosRunResult partialHotStartDrift(ChaosRequest request) {
        DispatchPhase3Support.ScenarioDefinition scenario = DispatchPhase3Support.scenarioDefinition(request.scenarioPack());
        Path feedbackDirectory = feedbackDirectory(request);
        RouteChainDispatchV2Properties properties = DispatchPhase3Support.baseProperties(
                DispatchPerfBenchmarkHarness.BaselineId.C,
                request.executionMode(),
                feedbackDirectory);
        scenario.configureProperties(properties, DispatchPerfBenchmarkHarness.BaselineId.C);
        var dependencies = scenario.dependencies(request.executionMode(), DispatchPerfBenchmarkHarness.BaselineId.C, properties);
        var harness = DispatchPhase3Support.harness(properties, dependencies);
        DispatchV2Request seed = scenario.request(request.workloadSize(), traceId(request, "seed"), DispatchPerfBenchmarkHarness.BaselineId.C);
        harness.core().dispatch(seed);
        harness.core().dispatch(DispatchHotStartCertificationHarness.copyWithTraceId(seed, traceId(request, "warm")));
        DispatchV2Request drifted = DispatchHotStartCertificationHarness.copyWithDecisionTime(
                seed,
                traceId(request, "hot-drift"),
                seed.decisionTime().plusSeconds(3600));
        DispatchV2Result result = harness.core().dispatch(drifted);
        return resultFromDispatch(request, result, result.hotStartState().degradeReasons(), 3, false);
    }

    private DispatchPhase3Support.ScenarioDependencies faultDependencies(DispatchPhase3Support.ChaosFaultType faultType,
                                                                         DispatchPhase3Support.ScenarioDependencies dependencies,
                                                                         RouteChainDispatchV2Properties properties) {
        return switch (faultType) {
            case TABULAR_UNAVAILABLE -> dependencies.withTabularClient(com.routechain.v2.integration.TestTabularScoringClient.notApplied("tabular-unavailable"));
            case ROUTEFINDER_UNAVAILABLE -> dependencies.withRouteFinderClient(com.routechain.v2.integration.TestRouteFinderClient.notApplied("routefinder-unavailable"));
            case GREEDRL_UNAVAILABLE -> dependencies.withGreedRlClient(com.routechain.v2.integration.TestGreedRlClient.notApplied("greedrl-unavailable"));
            case FORECAST_UNAVAILABLE -> dependencies.withForecastClient(TestForecastClient.notApplied("forecast-unavailable"));
            case WORKER_READY_FALSE_OPTIONAL_PATH -> dependencies.withForecastClient(readyFalseForecastClient());
            case OPEN_METEO_STALE -> {
                properties.setOpenMeteoEnabled(true);
                properties.getWeather().setEnabled(true);
                long staleAge = properties.getContext().getFreshness().getWeatherMaxAge().toMillis() + 1;
                yield dependencies.withOpenMeteoClient(TestOpenMeteoClient.staleHeavyRain(staleAge));
            }
            case OPEN_METEO_UNAVAILABLE -> {
                properties.setOpenMeteoEnabled(true);
                properties.getWeather().setEnabled(true);
                yield dependencies.withOpenMeteoClient(TestOpenMeteoClient.unavailable("open-meteo-unavailable"));
            }
            case TOMTOM_TIMEOUT -> {
                properties.setTomtomEnabled(true);
                properties.getTraffic().setEnabled(true);
                yield dependencies.withTomTomTrafficRefineClient(TestTomTomTrafficRefineClient.unavailable("tomtom-timeout"));
            }
            case TOMTOM_AUTH_OR_QUOTA -> {
                properties.setTomtomEnabled(true);
                properties.getTraffic().setEnabled(true);
                yield dependencies.withTomTomTrafficRefineClient(TestTomTomTrafficRefineClient.unavailable("tomtom-auth-or-quota-failed"));
            }
            case TOMTOM_HTTP_ERROR -> {
                properties.setTomtomEnabled(true);
                properties.getTraffic().setEnabled(true);
                yield dependencies.withTomTomTrafficRefineClient(TestTomTomTrafficRefineClient.unavailable("tomtom-http-error"));
            }
            case TOMTOM_MISSING_API_KEY -> {
                properties.setTomtomEnabled(true);
                properties.getTraffic().setEnabled(true);
                properties.getTraffic().setApiKey("");
                yield dependencies.withTomTomTrafficRefineClient(TestTomTomTrafficRefineClient.unavailable("tomtom-missing-api-key"));
            }
            default -> dependencies;
        };
    }

    private TestForecastClient readyFalseForecastClient() {
        MlWorkerMetadata metadata = new MlWorkerMetadata("chronos-2", "v1", "sha256:chronos", 11L);
        return new TestForecastClient(
                WorkerReadyState.notReady("worker-ready-false-optional-path", metadata),
                (feature, timeout) -> ForecastResult.notApplied("worker-ready-false-optional-path", metadata),
                (feature, timeout) -> ForecastResult.notApplied("worker-ready-false-optional-path", metadata),
                (feature, timeout) -> ForecastResult.notApplied("worker-ready-false-optional-path", metadata));
    }

    private DispatchChaosRunResult resultFromDispatch(ChaosRequest request,
                                                      DispatchV2Result result,
                                                      List<String> extraDegrades,
                                                      int dispatchCount,
                                                      boolean deferred) {
        List<String> degradeReasons = new ArrayList<>(DispatchPhase3Support.distinctDegrades(result));
        degradeReasons.addAll(extraDegrades);
        List<String> distinctDegrades = DispatchPhase3Support.distinctStrings(degradeReasons);
        List<String> mismatchReasons = correctnessMismatchReasons(result);
        boolean passed = deferred || (result.decisionStages().size() == 12 && mismatchReasons.isEmpty());
        return new DispatchChaosRunResult(
                "dispatch-chaos-run-result/v1",
                Instant.now(),
                DispatchPhase3Support.gitCommit(),
                DispatchPerfMachineProfile.capture(request.machineLabel()),
                request.executionMode().wireName(),
                request.scenarioPack().wireName(),
                request.scenarioPack().wireName(),
                request.faultType().wireName(),
                request.workloadSize().name(),
                dispatchCount,
                deferred,
                distinctDegrades,
                mismatchReasons,
                DispatchPhase3Support.conflictFreeAssignments(result),
                result.latencyBudgetSummary().breachedStageNames(),
                result.latencyBudgetSummary().totalBudgetBreached(),
                result.hotStartState().reusedStageNames(),
                passed,
                deferred ? List.of("deferred-existing-http-contract-only") : List.of());
    }

    private DispatchChaosRunResult deferredResult(ChaosRequest request, String note) {
        return new DispatchChaosRunResult(
                "dispatch-chaos-run-result/v1",
                Instant.now(),
                DispatchPhase3Support.gitCommit(),
                DispatchPerfMachineProfile.capture(request.machineLabel()),
                request.executionMode().wireName(),
                request.scenarioPack().wireName(),
                request.scenarioPack().wireName(),
                request.faultType().wireName(),
                request.workloadSize().name(),
                0,
                true,
                List.of(),
                List.of(),
                true,
                List.of(),
                false,
                List.of(),
                true,
                List.of(note));
    }

    private List<String> correctnessMismatchReasons(DispatchV2Result result) {
        List<String> reasons = new ArrayList<>();
        if (!DispatchPhase3Support.conflictFreeAssignments(result)) {
            reasons.add("conflict-detected");
        }
        if (result.decisionStages().size() != 12) {
            reasons.add("stage-count-mismatch");
        }
        return List.copyOf(reasons);
    }

    private void corruptLatestJson(Path directory, String oldValue, String newValue) {
        try {
            Path pointer = directory.resolve("latest.txt");
            String fileName = Files.readString(pointer).trim();
            Path filePath = directory.resolve(fileName);
            String current = Files.readString(filePath);
            Files.writeString(filePath, current.replace(oldValue, newValue));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to corrupt latest feedback file in " + directory, exception);
        }
    }

    private Path feedbackDirectory(ChaosRequest request) {
        return request.outputRoot()
                .resolve("feedback")
                .resolve("chaos")
                .resolve(request.faultType().wireName())
                .resolve(request.workloadSize().name().toLowerCase(Locale.ROOT));
    }

    private String traceId(ChaosRequest request, String suffix) {
        return "chaos-%s-%s-%s".formatted(
                request.faultType().wireName(),
                request.workloadSize().name().toLowerCase(Locale.ROOT),
                suffix);
    }

    public record ChaosRequest(
            DispatchPhase3Support.ChaosFaultType faultType,
            DispatchPerfBenchmarkHarness.WorkloadSize workloadSize,
            DispatchPhase3Support.ScenarioPack scenarioPack,
            DispatchPhase3Support.ExecutionMode executionMode,
            String machineLabel,
            Path outputRoot) {
    }
}
