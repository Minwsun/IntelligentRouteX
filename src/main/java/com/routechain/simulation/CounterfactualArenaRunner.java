package com.routechain.simulation;

import com.routechain.ai.OmegaDispatchAgent;
import com.routechain.domain.Enums.DeliveryServiceTier;
import com.routechain.domain.Enums.WeatherProfile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Counterfactual arena for policy-vs-policy comparisons on identical scenario tapes.
 */
public final class CounterfactualArenaRunner {
    private static final List<Long> SEEDS = List.of(42L, 77L, 123L);
    private static final int DEFAULT_DRIVERS = 50;
    private static final int DEFAULT_TICKS = 1200;
    private static final OrToolsShadowPolicySearch ORTOOLS_SHADOW = new OrToolsShadowPolicySearch();

    private static final List<ScenarioConfig> SCENARIOS = List.of(
            new ScenarioConfig("normal", DEFAULT_TICKS, 0.85, 0.30, WeatherProfile.CLEAR),
            new ScenarioConfig("rush_hour", DEFAULT_TICKS, 1.15, 0.58, WeatherProfile.CLEAR),
            new ScenarioConfig("demand_spike", DEFAULT_TICKS, 1.35, 0.45, WeatherProfile.LIGHT_RAIN),
            new ScenarioConfig("heavy_rain", DEFAULT_TICKS, 0.95, 0.54, WeatherProfile.HEAVY_RAIN),
            new ScenarioConfig("storm", DEFAULT_TICKS, 0.92, 0.62, WeatherProfile.STORM)
    );

    private static final PolicyConfig BASELINE = new PolicyConfig(
            "Legacy",
            SimulationEngine.DispatchMode.LEGACY,
            OmegaDispatchAgent.AblationMode.FULL);

    private static final List<PolicyConfig> CANDIDATES = List.of(
            new PolicyConfig("Omega-current", SimulationEngine.DispatchMode.OMEGA, OmegaDispatchAgent.AblationMode.FULL),
            new PolicyConfig("Omega-no-hold", SimulationEngine.DispatchMode.OMEGA, OmegaDispatchAgent.AblationMode.NO_HOLD),
            new PolicyConfig("Omega-no-reposition", SimulationEngine.DispatchMode.OMEGA, OmegaDispatchAgent.AblationMode.NO_REPOSITION),
            new PolicyConfig("Omega-no-continuation", SimulationEngine.DispatchMode.OMEGA, OmegaDispatchAgent.AblationMode.NO_CONTINUATION),
            new PolicyConfig("Omega-no-neural-prior", SimulationEngine.DispatchMode.OMEGA, OmegaDispatchAgent.AblationMode.NO_NEURAL_PRIOR)
    );

    private CounterfactualArenaRunner() {}

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0].trim().toLowerCase() : "full";
        List<Long> seeds = "smoke".equals(mode) ? List.of(SEEDS.get(0)) : SEEDS;
        List<ScenarioConfig> scenarios = "smoke".equals(mode)
                ? List.of(SCENARIOS.get(0))
                : SCENARIOS;
        BenchmarkEnvironmentProfile environmentProfile = BenchmarkEnvironmentProfile.detect(
                "local-production-small-50",
                DEFAULT_DRIVERS,
                SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name());

        Instant startedAt = Instant.now();
        List<PolicyCandidateRecord> policyCandidates = policyCandidates();
        List<CounterfactualRunSpec> runSpecs = buildRunSpecs(seeds, scenarios, policyCandidates);
        List<BenchmarkCaseSpec> caseSpecs = buildCaseSpecs(seeds, scenarios);
        BenchmarkRunManifest manifest = new BenchmarkRunManifest(
                BenchmarkSchema.VERSION,
                "counterfactual-arena-" + startedAt.toEpochMilli(),
                "counterfactualArena-" + mode,
                startedAt,
                resolveGitRevision(),
                seeds,
                List.of(DEFAULT_DRIVERS),
                environmentProfile,
                1,
                3,
                180,
                240_000L,
                "counterfactual,multi-seed,legacy-vs-candidates,ci95,effect-size,p-value",
                "Policy arena on shared scenario setup for AI-first backend tuning",
                caseSpecs,
                policyCandidates,
                runSpecs
        );
        BenchmarkArtifactWriter.writeManifest(manifest);

        Map<String, List<ReplayCompareResult>> comparesByScope = new LinkedHashMap<>();
        Map<String, List<RunReport>> runsByPolicy = new LinkedHashMap<>();

        for (ScenarioConfig scenario : scenarios) {
            for (long seed : seeds) {
                RunReport baseline = runScenario(seed, scenario, BASELINE);
                BenchmarkArtifactWriter.writeRun(baseline);
                runsByPolicy.computeIfAbsent(BASELINE.name, ignored -> new ArrayList<>()).add(baseline);

                for (PolicyConfig candidate : CANDIDATES) {
                    RunReport candidateRun = runScenario(seed, scenario, candidate);
                    BenchmarkArtifactWriter.writeRun(candidateRun);
                    runsByPolicy.computeIfAbsent(candidate.name, ignored -> new ArrayList<>()).add(candidateRun);

                    ReplayCompareResult compare = ReplayCompareResult.compare(baseline, candidateRun);
                    BenchmarkArtifactWriter.writeCompare(compare);

                    String scopedKey = candidate.name + "/" + scenario.name;
                    comparesByScope.computeIfAbsent(scopedKey, ignored -> new ArrayList<>()).add(compare);
                    comparesByScope.computeIfAbsent(candidate.name + "/all", ignored -> new ArrayList<>()).add(compare);
                }
            }
        }

        for (Map.Entry<String, List<ReplayCompareResult>> entry : comparesByScope.entrySet()) {
            String scope = entry.getKey();
            String candidateName = scope.substring(0, scope.indexOf('/'));
            emitAblation(scope, candidateName, entry.getValue());
        }

        emitLatencySloSummary(runsByPolicy);
        emitDriftSnapshots(runsByPolicy);
        System.out.println("[CounterfactualArena] completed. manifest=" + manifest.manifestId()
                + " scopes=" + comparesByScope.size());
    }

    private static List<BenchmarkCaseSpec> buildCaseSpecs(List<Long> seeds,
                                                           List<ScenarioConfig> scenarios) {
        List<BenchmarkCaseSpec> cases = new ArrayList<>();
        int replicate = 0;
        for (ScenarioConfig scenario : scenarios) {
            for (long seed : seeds) {
                for (PolicyConfig candidate : CANDIDATES) {
                    cases.add(new BenchmarkCaseSpec(
                            "CF-" + scenario.name + "-" + candidate.name + "-s" + seed,
                            "counterfactual-arena",
                            scenario.name,
                            DeliveryServiceTier.classifyScenario(scenario.name).wireValue(),
                            "local-production-small-50",
                            SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name(),
                            "simulation",
                            candidate.name,
                            scenario.ticks,
                            DEFAULT_DRIVERS,
                            scenario.demandMultiplier,
                            scenario.trafficIntensity,
                            scenario.weather.name(),
                            seed,
                            replicate++
                    ));
                }
            }
        }
        return cases;
    }

    private static List<CounterfactualRunSpec> buildRunSpecs(List<Long> seeds,
                                                             List<ScenarioConfig> scenarios,
                                                             List<PolicyCandidateRecord> policies) {
        List<String> policySet = policies.stream().map(PolicyCandidateRecord::policyId).toList();
        List<CounterfactualRunSpec> specs = new ArrayList<>();
        for (ScenarioConfig scenario : scenarios) {
            for (long seed : seeds) {
                specs.add(new CounterfactualRunSpec(
                        scenario.name,
                        seed,
                        DEFAULT_DRIVERS,
                        policySet,
                        240_000L
                ));
            }
        }
        return specs;
    }

    private static List<PolicyCandidateRecord> policyCandidates() {
        List<String> allScenarioNames = SCENARIOS.stream().map(s -> s.name).toList();
        return List.of(
                new PolicyCandidateRecord(
                        BASELINE.name,
                        SolverType.LEGACY_GREEDY,
                        "legacy-fixed",
                        "legacy",
                        allScenarioNames
                ),
                new PolicyCandidateRecord(
                        "Omega-current",
                        SolverType.TIMEFOLD_ONLINE,
                        "execution-first-hybrid",
                        "execution-first-default",
                        allScenarioNames
                ),
                new PolicyCandidateRecord(
                        "Omega-no-hold",
                        SolverType.OMEGA_ASSIGNMENT,
                        "execution-first-no-hold",
                        "execution-first-default",
                        allScenarioNames
                ),
                new PolicyCandidateRecord(
                        "Omega-no-reposition",
                        SolverType.OMEGA_ASSIGNMENT,
                        "execution-first-no-reposition",
                        "execution-first-default",
                        allScenarioNames
                ),
                new PolicyCandidateRecord(
                        "Omega-no-continuation",
                        SolverType.OMEGA_ASSIGNMENT,
                        "execution-first-no-continuation",
                        "execution-first-default",
                        allScenarioNames
                ),
                new PolicyCandidateRecord(
                        "Omega-no-neural-prior",
                        SolverType.OMEGA_ASSIGNMENT,
                        "execution-first-no-neural-prior",
                        "execution-first-default",
                        allScenarioNames
                ),
                new PolicyCandidateRecord(
                        "OR-Tools-shadow",
                        SolverType.ORTOOLS_SHADOW,
                        "offline-challenger",
                        "offline-eval",
                        List.of("normal", "rush_hour", "demand_spike")
                )
        );
    }

    private static RunReport runScenario(long seed,
                                         ScenarioConfig scenario,
                                         PolicyConfig policy) {
        SimulationEngine engine = new SimulationEngine(seed);
        engine.setDispatchMode(policy.dispatchMode);
        engine.setOmegaAblationMode(policy.ablationMode);
        engine.getOmegaAgent().setDiagnosticLoggingEnabled(false);
        engine.setExecutionProfile(OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);
        engine.setInitialDriverCount(DEFAULT_DRIVERS);
        engine.setDemandMultiplier(scenario.demandMultiplier);
        engine.setTrafficIntensity(scenario.trafficIntensity);
        engine.setWeatherProfile(scenario.weather);

        for (int i = 0; i < scenario.ticks; i++) {
            engine.tickHeadless();
        }
        RunReport report = engine.createRunReport(
                "counterfactual-" + scenario.name + "-" + policy.name + "-seed" + seed,
                seed
        );
        BenchmarkArtifactWriter.writeControlRoomFrame(engine.createControlRoomFrame(report));
        return report;
    }

    private static void emitAblation(String scope,
                                     String candidateName,
                                     List<ReplayCompareResult> compares) {
        if (compares == null || compares.isEmpty()) {
            return;
        }

        BenchmarkStatSummary gainSummary = BenchmarkStatistics.summarize(
                "overallGainPercent",
                scope,
                toList(compares, ReplayCompareResult::overallGainPercent));
        BenchmarkStatSummary completionDeltaSummary = BenchmarkStatistics.summarize(
                "completionRateDelta",
                scope,
                toList(compares, ReplayCompareResult::completionRateDelta));
        BenchmarkStatSummary deadheadDeltaSummary = BenchmarkStatistics.summarize(
                "deadheadDistanceRatioDelta",
                scope,
                toList(compares, ReplayCompareResult::deadheadRatioDelta));
        BenchmarkStatSummary launch3Summary = BenchmarkStatistics.summarize(
                "thirdOrderLaunchRateDelta",
                scope,
                toList(compares, ReplayCompareResult::thirdOrderLaunchRateDelta));
        BenchmarkStatSummary wait3Summary = BenchmarkStatistics.summarize(
                "waveAssemblyWaitRateDelta",
                scope,
                toList(compares, ReplayCompareResult::waveAssemblyWaitRateDelta));
        BenchmarkStatSummary holdConvSummary = BenchmarkStatistics.summarize(
                "holdConversionDelta",
                scope,
                toList(compares, compare -> compare.recoveryDelta() == null
                        ? 0.0
                        : compare.recoveryDelta().holdConvertedToWaveCountDelta()));

        BenchmarkArtifactWriter.writeStatSummary(gainSummary);
        BenchmarkArtifactWriter.writeStatSummary(completionDeltaSummary);
        BenchmarkArtifactWriter.writeStatSummary(deadheadDeltaSummary);
        BenchmarkArtifactWriter.writeStatSummary(launch3Summary);
        BenchmarkArtifactWriter.writeStatSummary(wait3Summary);
        BenchmarkArtifactWriter.writeStatSummary(holdConvSummary);

        BenchmarkArtifactWriter.writeAblationResult(new PolicyAblationResult(
                BenchmarkSchema.VERSION,
                "counterfactual-" + scope.replace('/', '-'),
                scope,
                BASELINE.name,
                candidateName,
                verdictFromGain(gainSummary.mean()),
                gainSummary.mean(),
                gainSummary,
                completionDeltaSummary,
                deadheadDeltaSummary,
                List.of(launch3Summary, wait3Summary, holdConvSummary)
        ));
    }

    private static void emitLatencySloSummary(Map<String, List<RunReport>> runsByPolicy) {
        for (Map.Entry<String, List<RunReport>> entry : runsByPolicy.entrySet()) {
            String policy = entry.getKey();
            List<RunReport> runs = entry.getValue();
            if (runs == null || runs.isEmpty()) {
                continue;
            }
            BenchmarkStatSummary latencySummary = BenchmarkStatistics.summarize(
                    "dispatchDecisionLatencyP95Ms",
                    "counterfactual/" + policy,
                    toList(runs, run -> run.latency().dispatchP95Ms()));
            BenchmarkStatSummary ortoolsShadowSummary = ORTOOLS_SHADOW.summarize(
                    "counterfactual/" + policy,
                    runs);
            BenchmarkArtifactWriter.writeStatSummary(latencySummary);
            BenchmarkArtifactWriter.writeStatSummary(ortoolsShadowSummary);
            if (latencySummary.mean() > 120.0) {
                System.out.println("[CounterfactualArena][WARN] SLO p95 latency > 120ms for policy "
                        + policy + " (dispatchP95Mean=" + String.format("%.1f", latencySummary.mean()) + "ms)");
            }
        }
    }

    private static void emitDriftSnapshots(Map<String, List<RunReport>> runsByPolicy) {
        List<RunReport> baselineRuns = runsByPolicy.get(BASELINE.name);
        if (baselineRuns == null || baselineRuns.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<RunReport>> entry : runsByPolicy.entrySet()) {
            String policy = entry.getKey();
            if (BASELINE.name.equals(policy)) {
                continue;
            }
            List<DispatchDriftSnapshot> snapshots = DispatchDriftMonitor.evaluate(
                    "counterfactual/" + policy,
                    baselineRuns,
                    entry.getValue()
            );
            for (DispatchDriftSnapshot snapshot : snapshots) {
                BenchmarkArtifactWriter.writeDriftSnapshot(snapshot);
                if (snapshot.drifted()) {
                    System.out.println("[CounterfactualArena][DRIFT] "
                            + snapshot.scope() + " metric=" + snapshot.metricName()
                            + " drift=" + String.format("%.3f", snapshot.drift())
                            + " threshold=" + String.format("%.3f", snapshot.threshold()));
                }
            }
        }
    }

    private static String verdictFromGain(double gain) {
        if (gain > 1.0) {
            return "CANDIDATE_BETTER";
        }
        if (gain < -1.0) {
            return "BASELINE_BETTER";
        }
        return "MIXED";
    }

    private static <T> List<Double> toList(List<T> items, java.util.function.ToDoubleFunction<T> fn) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<Double> values = new ArrayList<>(items.size());
        for (T item : items) {
            values.add(fn.applyAsDouble(item));
        }
        values.sort(Comparator.naturalOrder());
        return values;
    }

    private static String resolveGitRevision() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                    .redirectErrorStream(true)
                    .start();
            byte[] output = process.getInputStream().readAllBytes();
            int code = process.waitFor();
            if (code != 0) {
                return "unknown";
            }
            String value = new String(output, java.nio.charset.StandardCharsets.UTF_8).trim();
            return value.isBlank() ? "unknown" : value;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private record ScenarioConfig(
            String name,
            int ticks,
            double demandMultiplier,
            double trafficIntensity,
            WeatherProfile weather
    ) {}

    private record PolicyConfig(
            String name,
            SimulationEngine.DispatchMode dispatchMode,
            OmegaDispatchAgent.AblationMode ablationMode
    ) {}
}
