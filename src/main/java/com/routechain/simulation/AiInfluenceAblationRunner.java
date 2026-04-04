package com.routechain.simulation;

import com.routechain.ai.OmegaDispatchAgent;
import com.routechain.domain.Enums.WeatherProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Proves that key Omega AI components materially change routing outcomes by
 * comparing FULL against targeted ablation modes on identical scenario/seed tapes.
 */
public final class AiInfluenceAblationRunner {
    private static final String FULL_POLICY = "OMEGA_FULL";
    private static final List<OmegaDispatchAgent.AblationMode> REQUIRED_MODES = List.of(
            OmegaDispatchAgent.AblationMode.NO_NEURAL_PRIOR,
            OmegaDispatchAgent.AblationMode.NO_CONTINUATION,
            OmegaDispatchAgent.AblationMode.NO_REPOSITION,
            OmegaDispatchAgent.AblationMode.SMALL_BATCH_ONLY
    );

    private AiInfluenceAblationRunner() {}

    public static void main(String[] args) {
        String laneName = args.length > 0 && !args[0].isBlank()
                ? args[0].trim().toLowerCase(Locale.ROOT)
                : "smoke";
        BenchmarkCertificationScenarioMatrix.LaneDefinition lane =
                BenchmarkCertificationConfigLoader.loadScenarioMatrix().lane(laneName);
        List<ScenarioSpec> scenarios = canonicalScenarios(lane);

        System.out.println("=================================================");
        System.out.println("   ROUTECHAIN AI - INFLUENCE ABLATION (" + laneName.toUpperCase(Locale.ROOT) + ")");
        System.out.println("=================================================");

        for (OmegaDispatchAgent.AblationMode mode : REQUIRED_MODES) {
            List<ReplayCompareResult> compares = new ArrayList<>();
            for (ScenarioSpec scenario : scenarios) {
                for (long seed : lane.seeds()) {
                    RunReport ablated = runScenario(scenario, seed, mode);
                    RunReport full = runScenario(scenario, seed, OmegaDispatchAgent.AblationMode.FULL);
                    ReplayCompareResult compare = ReplayCompareResult.compare(ablated, full);
                    compares.add(compare);
                    System.out.println("  " + mode.name()
                            + " | " + scenario.name()
                            + " | seed=" + seed
                            + " :: " + compare.toSummary());
                }
            }
            BenchmarkArtifactWriter.writeAblationResult(toAblationSummary(laneName, mode, compares));
        }

        System.out.println("=================================================");
    }

    private static PolicyAblationResult toAblationSummary(String laneName,
                                                          OmegaDispatchAgent.AblationMode mode,
                                                          List<ReplayCompareResult> compares) {
        String scope = "ai-influence/" + laneName + "/" + mode.name().toLowerCase(Locale.ROOT);
        BenchmarkStatSummary gainSummary = BenchmarkStatistics.summarize(
                "overallGainPercent",
                scope,
                toValues(compares, ReplayCompareResult::overallGainPercent));
        BenchmarkStatSummary completionSummary = BenchmarkStatistics.summarize(
                "completionRateDelta",
                scope,
                toValues(compares, ReplayCompareResult::completionRateDelta));
        BenchmarkStatSummary deadheadSummary = BenchmarkStatistics.summarize(
                "deadheadDistanceRatioDelta",
                scope,
                toValues(compares, ReplayCompareResult::deadheadRatioDelta));
        BenchmarkStatSummary postDropSummary = BenchmarkStatistics.summarize(
                "postDropOrderHitRateDelta",
                scope,
                toValues(compares, ReplayCompareResult::postDropOrderHitRateDelta));
        BenchmarkStatSummary routingScoreSummary = BenchmarkStatistics.summarize(
                "routingScoreDelta",
                scope,
                toValues(compares, compare -> compare.intelligenceDelta().routingScoreDelta()));
        BenchmarkStatSummary networkScoreSummary = BenchmarkStatistics.summarize(
                "networkScoreDelta",
                scope,
                toValues(compares, compare -> compare.intelligenceDelta().networkScoreDelta()));

        return new PolicyAblationResult(
                BenchmarkSchema.VERSION,
                "ai-influence-" + laneName + "-" + mode.name().toLowerCase(Locale.ROOT),
                scope,
                mode.name(),
                FULL_POLICY,
                verdictFromGain(gainSummary.mean()),
                gainSummary.mean(),
                gainSummary,
                completionSummary,
                deadheadSummary,
                List.of(postDropSummary, routingScoreSummary, networkScoreSummary)
        );
    }

    private static String verdictFromGain(double gain) {
        if (gain > 0.5) {
            return "FULL_BETTER";
        }
        if (gain < -0.5) {
            return "ABLATION_BETTER";
        }
        return "MIXED";
    }

    private static List<Double> toValues(List<ReplayCompareResult> compares,
                                         ToDoubleFunction<ReplayCompareResult> extractor) {
        List<Double> values = new ArrayList<>(compares.size());
        for (ReplayCompareResult compare : compares) {
            values.add(extractor.applyAsDouble(compare));
        }
        return values;
    }

    private static List<ScenarioSpec> canonicalScenarios(BenchmarkCertificationScenarioMatrix.LaneDefinition lane) {
        Map<String, ScenarioSpec> byGroup = new LinkedHashMap<>();
        byGroup.put("CLEAR", new ScenarioSpec("instant-normal", 1200, 50, 0.85, 0.30, WeatherProfile.CLEAR));
        byGroup.put("LIGHT_RAIN", new ScenarioSpec("instant-rain_onset", 1200, 50, 1.05, 0.40, WeatherProfile.LIGHT_RAIN));
        byGroup.put("RUSH_HOUR", new ScenarioSpec("instant-rush_hour", 1200, 50, 1.15, 0.58, WeatherProfile.CLEAR));
        byGroup.put("DEMAND_SPIKE", new ScenarioSpec("instant-demand_spike", 1200, 50, 1.35, 0.45, WeatherProfile.LIGHT_RAIN));
        byGroup.put("HEAVY_RAIN", new ScenarioSpec("heavy_rain", 1200, 50, 0.95, 0.54, WeatherProfile.HEAVY_RAIN));
        byGroup.put("SHORTAGE", new ScenarioSpec("post_drop_shortage", 1200, 26, 1.05, 0.42, WeatherProfile.CLEAR));

        List<ScenarioSpec> scenarios = new ArrayList<>();
        for (BenchmarkCertificationScenarioMatrix.ScenarioBucket bucket : lane.scenarioBuckets()) {
            ScenarioSpec spec = byGroup.get(bucket.scenarioGroup());
            if (spec != null) {
                scenarios.add(spec);
            }
        }
        return scenarios;
    }

    private static RunReport runScenario(ScenarioSpec scenario,
                                         long seed,
                                         OmegaDispatchAgent.AblationMode ablationMode) {
        SimulationEngine engine = new SimulationEngine(seed);
        engine.setDispatchMode(SimulationEngine.DispatchMode.OMEGA);
        engine.setOmegaAblationMode(ablationMode);
        engine.getOmegaAgent().setDiagnosticLoggingEnabled(false);
        engine.setExecutionProfile(OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);
        engine.setInitialDriverCount(scenario.drivers());
        engine.setDemandMultiplier(scenario.demandMultiplier());
        engine.setTrafficIntensity(scenario.trafficIntensity());
        engine.setWeatherProfile(scenario.weatherProfile());

        for (int i = 0; i < scenario.ticks(); i++) {
            engine.tickHeadless();
        }

        return engine.createRunReport(
                scenario.name()
                        + "-ai-proof-"
                        + ablationMode.name().toLowerCase(Locale.ROOT)
                        + "-seed" + seed,
                seed
        );
    }

    private record ScenarioSpec(
            String name,
            int ticks,
            int drivers,
            double demandMultiplier,
            double trafficIntensity,
            WeatherProfile weatherProfile
    ) {}
}
