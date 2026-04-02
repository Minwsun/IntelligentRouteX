package com.routechain.simulation;

import com.routechain.ai.OmegaDispatchAgent;
import com.routechain.domain.Enums.DeliveryServiceTier;
import com.routechain.domain.Enums.WeatherProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Hybrid benchmark harness:
 * 1) Track A: production-realism multi-seed stress runs.
 * 2) Track B: research-standard adapter for Solomon/Homberger-style instances.
 */
public final class HybridBenchmarkRunner {

    private static final List<Long> SEEDS = List.of(42L, 77L, 123L);
    private static final List<Integer> DRIVER_PROFILES = List.of(10, 25, 50);

    private static final List<RealismScenario> TRACK_A_SCENARIOS = List.of(
            new RealismScenario("instant-normal", SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name(), 1200, 0.85, 0.30, WeatherProfile.CLEAR),
            new RealismScenario("instant-rush_hour", SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name(), 1200, 1.15, 0.58, WeatherProfile.CLEAR),
            new RealismScenario("instant-demand_spike", SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name(), 1200, 1.35, 0.45, WeatherProfile.LIGHT_RAIN),
            new RealismScenario("instant-rain_onset", SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name(), 1200, 1.05, 0.40, WeatherProfile.LIGHT_RAIN),
            new RealismScenario("2h-fill_wave", SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name(), 1200, 0.92, 0.35, WeatherProfile.CLEAR),
            new RealismScenario("4h-consolidation", SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name(), 1200, 0.78, 0.28, WeatherProfile.CLEAR),
            new RealismScenario("multi_stop_cod", SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name(), 1200, 0.88, 0.36, WeatherProfile.CLEAR),
            new RealismScenario("post_drop_shortage", SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name(), 1200, 1.05, 0.42, WeatherProfile.CLEAR),
            new RealismScenario("merchant_cluster", SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name(), 1200, 1.10, 0.34, WeatherProfile.CLEAR),
            new RealismScenario("heavy_rain", SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name(), 1200, 0.95, 0.54, WeatherProfile.HEAVY_RAIN),
            new RealismScenario("storm", SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name(), 1200, 0.92, 0.62, WeatherProfile.STORM)
    );

    private static final int RESEARCH_CASES_PER_FAMILY_LIMIT = 12;
    private static final Path RESEARCH_ROOT = Path.of("benchmarks", "vrp");
    private static final OrToolsShadowPolicySearch ORTOOLS_SHADOW = new OrToolsShadowPolicySearch();
    private static final List<PolicyCandidateRecord> POLICY_CANDIDATES = List.of(
            new PolicyCandidateRecord(
                    "Legacy",
                    SolverType.LEGACY_GREEDY,
                    "legacy-fixed",
                    "legacy",
                    List.of("all")
            ),
            new PolicyCandidateRecord(
                    "Omega-current",
                    SolverType.TIMEFOLD_ONLINE,
                    "execution-first-hybrid",
                    "execution-first-default",
                    List.of("all")
            ),
            new PolicyCandidateRecord(
                    "OR-Tools-shadow",
                    SolverType.ORTOOLS_SHADOW,
                    "offline-challenger",
                    "offline-eval",
                    List.of("normal", "rush_hour", "demand_spike", "research-standard")
            )
    );

    private HybridBenchmarkRunner() {}

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0].trim().toLowerCase() : "all";
        boolean runTrackA = mode.equals("all") || mode.equals("tracka") || mode.equals("production");
        boolean runTrackB = mode.equals("all") || mode.equals("trackb") || mode.equals("research");

        Instant start = Instant.now();
        List<BenchmarkCaseSpec> plannedCases = new ArrayList<>();
        if (runTrackA) {
            plannedCases.addAll(buildTrackACases());
        }
        if (runTrackB) {
            plannedCases.addAll(buildResearchCases());
        }

        BenchmarkRunManifest manifest = new BenchmarkRunManifest(
                BenchmarkSchema.VERSION,
                "hybrid-benchmark-" + start.toEpochMilli(),
                mode.equals("all") ? "hybridBenchmark" : "hybridBenchmark-" + mode,
                start,
                resolveGitRevision(),
                SEEDS,
                DRIVER_PROFILES,
                BenchmarkEnvironmentProfile.detect(
                        "local-production-small-50",
                        50,
                        SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name()),
                1,
                3,
                180,
                240_000L,
                "trackA+trackB,multi-seed,ci95,effect-size,p-value",
                "Backend-only production core benchmark harness",
                plannedCases,
                POLICY_CANDIDATES,
                List.of()
        );
        BenchmarkArtifactWriter.writeManifest(manifest);

        List<CaseRun> trackAResults = new ArrayList<>();
        List<CaseRun> trackBResults = new ArrayList<>();

        if (runTrackA) {
            System.out.println("=================================================");
            System.out.println("   HYBRID BENCHMARK - TRACK A (PRODUCTION REALISM)");
            System.out.println("=================================================");
            for (BenchmarkCaseSpec spec : buildTrackACases()) {
                CaseRun run = executeCase(spec);
                trackAResults.add(run);
                logCase(run);
            }
            emitTrackStats("trackA", trackAResults);
        }

        if (runTrackB) {
            System.out.println("=================================================");
            System.out.println("   HYBRID BENCHMARK - TRACK B (RESEARCH STANDARD)");
            System.out.println("=================================================");
            List<BenchmarkCaseSpec> researchCases = buildResearchCases();
            if (researchCases.isEmpty()) {
                System.out.println("[HybridBenchmark] No Solomon/Homberger files found under "
                        + RESEARCH_ROOT + ". Track B skipped.");
            } else {
                for (BenchmarkCaseSpec spec : researchCases) {
                    CaseRun run = executeCase(spec);
                    trackBResults.add(run);
                    logCase(run);
                }
                emitTrackStats("trackB", trackBResults);
            }
        }

        List<CaseRun> all = new ArrayList<>(trackAResults.size() + trackBResults.size());
        all.addAll(trackAResults);
        all.addAll(trackBResults);
        emitTrackStats("global", all);
        System.out.println("=================================================");
        System.out.println("[HybridBenchmark] Done. totalCases=" + all.size()
                + " manifestId=" + manifest.manifestId());
        System.out.println("=================================================");
    }

    private static void logCase(CaseRun run) {
        System.out.println("  " + run.spec.caseId() + " :: " + run.compare.toSummary());
    }

    private static List<BenchmarkCaseSpec> buildTrackACases() {
        List<BenchmarkCaseSpec> cases = new ArrayList<>();
        int index = 0;
        for (RealismScenario scenario : TRACK_A_SCENARIOS) {
            for (int drivers : DRIVER_PROFILES) {
                for (long seed : SEEDS) {
                    cases.add(new BenchmarkCaseSpec(
                            "A-" + scenario.name + "-d" + drivers + "-s" + seed,
                            "production-realism",
                            scenario.name,
                            DeliveryServiceTier.classifyScenario(scenario.name).wireValue(),
                            "local-production-small-" + drivers,
                            scenario.routeLatencyMode,
                            "simulation",
                            "trackA:" + scenario.name,
                            scenario.ticks,
                            drivers,
                            scenario.demandMultiplier,
                            scenario.trafficIntensity,
                            scenario.weatherProfile.name(),
                            seed,
                            index++
                    ));
                }
            }
        }
        return cases;
    }

    private static List<BenchmarkCaseSpec> buildResearchCases() {
        List<BenchmarkCaseSpec> cases = new ArrayList<>();
        cases.addAll(buildResearchCasesForFamily("solomon"));
        cases.addAll(buildResearchCasesForFamily("homberger"));
        return cases;
    }

    private static List<BenchmarkCaseSpec> buildResearchCasesForFamily(String family) {
        Path familyRoot = RESEARCH_ROOT.resolve(family);
        if (Files.notExists(familyRoot)) {
            return List.of();
        }

        List<Path> datasetFiles = new ArrayList<>();
        try (var stream = Files.walk(familyRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(HybridBenchmarkRunner::isResearchDatasetFile)
                    .sorted(Comparator.naturalOrder())
                    .limit(RESEARCH_CASES_PER_FAMILY_LIMIT)
                    .forEach(datasetFiles::add);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to scan research dataset folder: " + familyRoot, e);
        }

        List<BenchmarkCaseSpec> cases = new ArrayList<>();
        int replicate = 0;
        for (Path dataset : datasetFiles) {
            ResearchProfile profile = inferResearchProfile(dataset.getFileName().toString(), family);
            for (long seed : SEEDS) {
                cases.add(new BenchmarkCaseSpec(
                        "B-" + family + "-" + profile.datasetTag + "-s" + seed,
                        "research-standard",
                        profile.scenarioName,
                        DeliveryServiceTier.classifyScenario(profile.scenarioName).wireValue(),
                        "local-production-small-" + profile.drivers,
                        SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name(),
                        family,
                        dataset.toString(),
                        profile.ticks,
                        profile.drivers,
                        profile.demandMultiplier,
                        profile.trafficIntensity,
                        profile.weatherProfile.name(),
                        seed,
                        replicate++
                ));
            }
        }
        return cases;
    }

    private static boolean isResearchDatasetFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".txt") || name.endsWith(".vrp") || name.endsWith(".csv");
    }

    private static ResearchProfile inferResearchProfile(String fileName, String family) {
        String upper = fileName.toUpperCase();
        if (upper.startsWith("RC")) {
            return new ResearchProfile("mixed_timewindow", "rc", 1500, 50, 1.18, 0.60, WeatherProfile.LIGHT_RAIN);
        }
        if (upper.startsWith("R")) {
            return new ResearchProfile("distributed_customers", "r", 1500, 50, 1.22, 0.64, WeatherProfile.CLEAR);
        }
        if (upper.startsWith("C")) {
            return new ResearchProfile("clustered_customers", "c", 1400, 25, 0.96, 0.38, WeatherProfile.CLEAR);
        }
        if ("homberger".equalsIgnoreCase(family)) {
            return new ResearchProfile("large_scale_timewindow", "h", 1800, 50, 1.25, 0.66, WeatherProfile.LIGHT_RAIN);
        }
        return new ResearchProfile("research_generic", "x", 1500, 25, 1.05, 0.50, WeatherProfile.CLEAR);
    }

    private static CaseRun executeCase(BenchmarkCaseSpec spec) {
        RunReport legacy = runCase(spec, SimulationEngine.DispatchMode.LEGACY);
        RunReport omega = runCase(spec, SimulationEngine.DispatchMode.OMEGA);
        ReplayCompareResult compare = ReplayCompareResult.compare(legacy, omega);
        BenchmarkArtifactWriter.writeCompare(compare);
        return new CaseRun(spec, legacy, omega, compare);
    }

    private static RunReport runCase(BenchmarkCaseSpec spec, SimulationEngine.DispatchMode mode) {
        SimulationEngine engine = new SimulationEngine(spec.seed());
        engine.setDispatchMode(mode);
        engine.getOmegaAgent().setDiagnosticLoggingEnabled(false);
        engine.setOmegaAblationMode(OmegaDispatchAgent.AblationMode.FULL);
        engine.setExecutionProfile(OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);
        engine.setInitialDriverCount(spec.drivers());
        engine.setDemandMultiplier(spec.demandMultiplier());
        engine.setTrafficIntensity(spec.trafficIntensity());
        engine.setWeatherProfile(WeatherProfile.valueOf(spec.weatherProfile()));
        engine.setRouteLatencyMode(SimulationEngine.RouteLatencyMode.valueOf(spec.routeLatencyMode()));

        for (int i = 0; i < spec.ticks(); i++) {
            engine.tickHeadless();
        }

        RunReport report = engine.createRunReport(
                spec.caseId() + "-" + mode.name().toLowerCase() + "-seed" + spec.seed(),
                spec.seed()
        );
        BenchmarkArtifactWriter.writeRun(report);
        BenchmarkArtifactWriter.writeControlRoomFrame(engine.createControlRoomFrame(report));
        return report;
    }

    private static void emitTrackStats(String scopePrefix, List<CaseRun> runs) {
        if (runs == null || runs.isEmpty()) {
            return;
        }

        Map<String, List<CaseRun>> byScenario = new HashMap<>();
        byScenario.put(scopePrefix + "/all", runs);
        for (CaseRun run : runs) {
            byScenario.computeIfAbsent(scopePrefix + "/" + run.spec.scenarioName(), key -> new ArrayList<>())
                    .add(run);
        }

        for (Map.Entry<String, List<CaseRun>> entry : byScenario.entrySet()) {
            String scope = entry.getKey();
            List<CaseRun> scoped = entry.getValue();
            BenchmarkStatSummary gainSummary = BenchmarkStatistics.summarize(
                    "overallGainPercent",
                    scope,
                    toDoubleList(scoped, run -> run.compare.overallGainPercent()));
            BenchmarkStatSummary completionSummary = BenchmarkStatistics.summarizeComparison(
                    "completionRate",
                    scope,
                    toDoubleList(scoped, run -> run.legacy.completionRate()),
                    toDoubleList(scoped, run -> run.omega.completionRate()));
            BenchmarkStatSummary deadheadSummary = BenchmarkStatistics.summarizeComparison(
                    "deadheadDistanceRatio",
                    scope,
                    toDoubleList(scoped, run -> run.legacy.deadheadDistanceRatio()),
                    toDoubleList(scoped, run -> run.omega.deadheadDistanceRatio()));
            BenchmarkStatSummary launch3Summary = BenchmarkStatistics.summarizeComparison(
                    "thirdOrderLaunchRate",
                    scope,
                    toDoubleList(scoped, run -> run.legacy.thirdOrderLaunchRate()),
                    toDoubleList(scoped, run -> run.omega.thirdOrderLaunchRate()));
            BenchmarkStatSummary wait3Summary = BenchmarkStatistics.summarizeComparison(
                    "waveAssemblyWaitRate",
                    scope,
                    toDoubleList(scoped, run -> run.legacy.waveAssemblyWaitRate()),
                    toDoubleList(scoped, run -> run.omega.waveAssemblyWaitRate()));
            BenchmarkStatSummary dhCompletedSummary = BenchmarkStatistics.summarizeComparison(
                    "deadheadPerCompletedOrderKm",
                    scope,
                    toDoubleList(scoped, run -> run.legacy.deadheadPerCompletedOrderKm()),
                    toDoubleList(scoped, run -> run.omega.deadheadPerCompletedOrderKm()));
            BenchmarkStatSummary latencySummary = BenchmarkStatistics.summarizeComparison(
                    "dispatchDecisionLatencyP95Ms",
                    scope,
                    toDoubleList(scoped, run -> run.legacy.latency().dispatchP95Ms()),
                    toDoubleList(scoped, run -> run.omega.latency().dispatchP95Ms()));
            BenchmarkStatSummary ortoolsShadowObjective = ORTOOLS_SHADOW.summarize(
                    scope,
                    scoped.stream().map(caseRun -> caseRun.omega).toList());

            BenchmarkArtifactWriter.writeStatSummary(gainSummary);
            BenchmarkArtifactWriter.writeStatSummary(completionSummary);
            BenchmarkArtifactWriter.writeStatSummary(deadheadSummary);
            BenchmarkArtifactWriter.writeStatSummary(launch3Summary);
            BenchmarkArtifactWriter.writeStatSummary(wait3Summary);
            BenchmarkArtifactWriter.writeStatSummary(dhCompletedSummary);
            BenchmarkArtifactWriter.writeStatSummary(latencySummary);
            BenchmarkArtifactWriter.writeStatSummary(ortoolsShadowObjective);
            BenchmarkArtifactWriter.writeAblationResult(new PolicyAblationResult(
                    BenchmarkSchema.VERSION,
                    "ablation-" + scope.replace('/', '-'),
                    scope,
                    "Legacy",
                    "Omega-current",
                    verdictFromGain(gainSummary.mean()),
                    gainSummary.mean(),
                    gainSummary,
                    completionSummary,
                    deadheadSummary,
                    List.of(launch3Summary, wait3Summary, dhCompletedSummary, latencySummary, ortoolsShadowObjective)
            ));
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

    private static <T> List<Double> toDoubleList(List<T> values, ToDoubleFunction<T> extractor) {
        List<Double> out = new ArrayList<>(values.size());
        for (T value : values) {
            out.add(extractor.applyAsDouble(value));
        }
        return out;
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

    private record CaseRun(
            BenchmarkCaseSpec spec,
            RunReport legacy,
            RunReport omega,
            ReplayCompareResult compare
    ) {}

    private record RealismScenario(
            String name,
            String routeLatencyMode,
            int ticks,
            double demandMultiplier,
            double trafficIntensity,
            WeatherProfile weatherProfile
    ) {}

    private record ResearchProfile(
            String scenarioName,
            String datasetTag,
            int ticks,
            int drivers,
            double demandMultiplier,
            double trafficIntensity,
            WeatherProfile weatherProfile
    ) {}
}
