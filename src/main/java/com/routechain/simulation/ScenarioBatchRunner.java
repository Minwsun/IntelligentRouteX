package com.routechain.simulation;

import com.routechain.ai.OmegaDispatchAgent;
import com.routechain.domain.Enums.WeatherProfile;

import java.util.List;

/**
 * Runs a small portfolio of benchmark scenarios and compares
 * the legacy dispatch lane against the Omega driver-centric lane.
 */
public class ScenarioBatchRunner {

    private record ScenarioConfig(
            String name,
            int ticks,
            int drivers,
            double demandMultiplier,
            double trafficIntensity,
            WeatherProfile weatherProfile
    ) {}

    private static final List<ScenarioConfig> SCENARIOS = List.of(
            new ScenarioConfig("normal", 1200, 40, 0.8, 0.30, WeatherProfile.CLEAR),
            new ScenarioConfig("rush_hour", 1200, 40, 1.1, 0.55, WeatherProfile.CLEAR),
            new ScenarioConfig("heavy_rain", 1200, 40, 0.9, 0.50, WeatherProfile.HEAVY_RAIN),
            new ScenarioConfig("demand_spike", 1200, 40, 1.35, 0.45, WeatherProfile.LIGHT_RAIN),
            new ScenarioConfig("shortage", 1200, 26, 1.0, 0.40, WeatherProfile.CLEAR)
    );

    public static void main(String[] args) {
        if (args.length > 0 && "ablation".equalsIgnoreCase(args[0])) {
            runAblationBatch();
            return;
        }

        System.out.println("=================================================");
        System.out.println("   ROUTECHAIN AI - SCENARIO BATCH COMPARISON");
        System.out.println("=================================================");

        for (ScenarioConfig scenario : SCENARIOS) {
            RunReport legacy = runScenario(scenario, SimulationEngine.DispatchMode.LEGACY);
            RunReport omega = runScenario(scenario, SimulationEngine.DispatchMode.OMEGA);
            ReplayCompareResult compare = ReplayCompareResult.compare(legacy, omega);

            System.out.println();
            System.out.println("Scenario: " + scenario.name());
            System.out.println("  Legacy : " + legacy.toSummary());
            System.out.println("  Omega  : " + omega.toSummary());
            System.out.println("  Compare: " + compare.toSummary());
        }

        System.out.println("=================================================");
    }

    private static void runAblationBatch() {
        System.out.println("=================================================");
        System.out.println("   ROUTECHAIN AI - OMEGA ABLATION BATCH");
        System.out.println("=================================================");

        for (ScenarioConfig scenario : SCENARIOS) {
            RunReport full = runScenario(
                    scenario,
                    SimulationEngine.DispatchMode.OMEGA,
                    OmegaDispatchAgent.AblationMode.FULL);

            System.out.println();
            System.out.println("Scenario: " + scenario.name());
            System.out.println("  Omega FULL: " + full.toSummary());

            for (OmegaDispatchAgent.AblationMode mode : OmegaDispatchAgent.AblationMode.values()) {
                if (mode == OmegaDispatchAgent.AblationMode.FULL) {
                    continue;
                }
                RunReport ablated = runScenario(
                        scenario,
                        SimulationEngine.DispatchMode.OMEGA,
                        mode);
                ReplayCompareResult compare = ReplayCompareResult.compare(ablated, full);
                System.out.println("  " + mode.name() + ": " + ablated.toSummary());
                System.out.println("    Impact: " + compare.toSummary());
            }
        }

        System.out.println("=================================================");
    }

    private static RunReport runScenario(
            ScenarioConfig scenario,
            SimulationEngine.DispatchMode mode) {
        return runScenario(scenario, mode, OmegaDispatchAgent.AblationMode.FULL);
    }

    private static RunReport runScenario(
            ScenarioConfig scenario,
            SimulationEngine.DispatchMode mode,
            OmegaDispatchAgent.AblationMode ablationMode) {

        SimulationEngine engine = new SimulationEngine();
        engine.setDispatchMode(mode);
        engine.getOmegaAgent().setDiagnosticLoggingEnabled(false);
        engine.setOmegaAblationMode(ablationMode);
        engine.setInitialDriverCount(scenario.drivers());
        engine.setDemandMultiplier(scenario.demandMultiplier());
        engine.setTrafficIntensity(scenario.trafficIntensity());
        engine.setWeatherProfile(scenario.weatherProfile());

        for (int i = 0; i < scenario.ticks(); i++) {
            engine.tickHeadless();
        }

        return engine.createRunReport(
                scenario.name() + "-" + mode.name().toLowerCase()
                        + "-" + ablationMode.name().toLowerCase(),
                42L
        );
    }
}
