package com.routechain.simulation;

import com.routechain.ai.OmegaDispatchAgent;
import com.routechain.domain.Enums.WeatherProfile;

import java.util.List;

/**
 * Runs a small portfolio of benchmark scenarios and compares
 * the legacy dispatch lane against the Omega driver-centric lane.
 */
public class ScenarioBatchRunner {

    @FunctionalInterface
    private interface ScenarioSetup {
        void configure(ScenarioShockEngine shockEngine);
    }

    private static final ScenarioSetup NO_OP_SETUP = engine -> { };

    private record ScenarioConfig(
            String name,
            int ticks,
            int drivers,
            double demandMultiplier,
            double trafficIntensity,
            WeatherProfile weatherProfile,
            ScenarioSetup setup
    ) {}

    private static ScenarioConfig scenario(
            String name,
            int ticks,
            int drivers,
            double demandMultiplier,
            double trafficIntensity,
            WeatherProfile weatherProfile) {
        return new ScenarioConfig(name, ticks, drivers, demandMultiplier,
                trafficIntensity, weatherProfile, NO_OP_SETUP);
    }

    private static ScenarioConfig scenario(
            String name,
            int ticks,
            int drivers,
            double demandMultiplier,
            double trafficIntensity,
            WeatherProfile weatherProfile,
            ScenarioSetup setup) {
        return new ScenarioConfig(name, ticks, drivers, demandMultiplier,
                trafficIntensity, weatherProfile, setup == null ? NO_OP_SETUP : setup);
    }

    private static final List<ScenarioConfig> SCENARIOS = List.of(
            scenario("normal", 1200, 40, 0.8, 0.30, WeatherProfile.CLEAR),
            scenario("rush_hour", 1200, 40, 1.1, 0.55, WeatherProfile.CLEAR),
            scenario("heavy_rain", 1200, 40, 0.9, 0.50, WeatherProfile.HEAVY_RAIN),
            scenario("demand_spike", 1200, 40, 1.35, 0.45, WeatherProfile.LIGHT_RAIN),
            scenario("shortage", 1200, 26, 1.0, 0.40, WeatherProfile.CLEAR)
    );

    private static final List<ScenarioConfig> SHOWCASE_SCENARIOS = List.of(
            scenario("showcase_pickup_wave", 1200, 42, 1.65, 0.28, WeatherProfile.CLEAR,
                    ScenarioBatchRunner::configureShowcasePickupWave),
            scenario("merchant_cluster_wave", 1200, 42, 1.55, 0.26, WeatherProfile.CLEAR,
                    ScenarioBatchRunner::configureMerchantClusterWave),
            scenario("soft_landing_corridor", 1200, 40, 1.08, 0.32, WeatherProfile.CLEAR,
                    ScenarioBatchRunner::configureSoftLandingCorridor)
    );
    private static final List<ScenarioConfig> STRESS_SCENARIOS = List.of(
            scenario("normal", 1200, 40, 0.8, 0.30, WeatherProfile.CLEAR),
            scenario("rush_hour", 1200, 40, 1.1, 0.55, WeatherProfile.CLEAR),
            scenario("demand_spike", 1200, 40, 1.35, 0.45, WeatherProfile.LIGHT_RAIN),
            scenario("heavy_rain", 1200, 40, 0.9, 0.50, WeatherProfile.HEAVY_RAIN)
    );
    private static final List<Long> DEV_TUNING_SEEDS = List.of(42L, 77L, 123L);
    private static final List<Long> SHOWCASE_HOLDOUT_SEEDS = List.of(211L, 307L, 509L);

    public static void main(String[] args) {
        if (args.length > 0 && "ablation".equalsIgnoreCase(args[0])) {
            runAblationBatch();
            return;
        }
        if (args.length > 0 && "showcase".equalsIgnoreCase(args[0])) {
            runShowcaseBatch();
            return;
        }
        if (args.length > 0 && "stress".equalsIgnoreCase(args[0])) {
            runStressTuneBatch();
            return;
        }

        System.out.println("=================================================");
        System.out.println("   ROUTECHAIN AI - SCENARIO BATCH COMPARISON");
        System.out.println("=================================================");

        for (ScenarioConfig scenario : SCENARIOS) {
            RunReport legacy = runScenario(
                    scenario,
                    SimulationEngine.DispatchMode.LEGACY,
                    OmegaDispatchAgent.AblationMode.FULL,
                    OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);
            RunReport omega = runScenario(
                    scenario,
                    SimulationEngine.DispatchMode.OMEGA,
                    OmegaDispatchAgent.AblationMode.FULL,
                    OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);
            ReplayCompareResult compare = ReplayCompareResult.compare(legacy, omega);
            BenchmarkArtifactWriter.writeCompare(compare);

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
                    OmegaDispatchAgent.AblationMode.FULL,
                    OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);

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
                        mode,
                        OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);
                ReplayCompareResult compare = ReplayCompareResult.compare(ablated, full);
                BenchmarkArtifactWriter.writeCompare(compare);
                System.out.println("  " + mode.name() + ": " + ablated.toSummary());
                System.out.println("    Impact: " + compare.toSummary());
            }
        }

        System.out.println("=================================================");
    }

    private static void runShowcaseBatch() {
        System.out.println("=================================================");
        System.out.println("   ROUTECHAIN AI - SHOWCASE NARRATIVE BATCH");
        System.out.println("=================================================");

        for (ScenarioConfig scenario : SHOWCASE_SCENARIOS) {
            System.out.println();
            System.out.println("Scenario: " + scenario.name());

            List<RunReport> legacyRuns = new java.util.ArrayList<>();
            List<RunReport> mainlineRuns = new java.util.ArrayList<>();
            List<RunReport> showcaseRuns = new java.util.ArrayList<>();
            List<ReplayCompareResult> legacyToMainlineRuns = new java.util.ArrayList<>();
            List<ReplayCompareResult> mainlineToShowcaseRuns = new java.util.ArrayList<>();

            for (long seed : SHOWCASE_HOLDOUT_SEEDS) {
                RunReport legacy = runScenario(
                        scenario,
                        SimulationEngine.DispatchMode.LEGACY,
                        OmegaDispatchAgent.AblationMode.FULL,
                        OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC,
                        seed);
                RunReport mainline = runScenario(
                        scenario,
                        SimulationEngine.DispatchMode.OMEGA,
                        OmegaDispatchAgent.AblationMode.FULL,
                        OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC,
                        seed);
                RunReport showcase = runScenario(
                        scenario,
                        SimulationEngine.DispatchMode.OMEGA,
                        OmegaDispatchAgent.AblationMode.FULL,
                        OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8,
                        seed);
                ReplayCompareResult legacyToMainline = ReplayCompareResult.compare(legacy, mainline);
                ReplayCompareResult mainlineToShowcase = ReplayCompareResult.compare(mainline, showcase);
                BenchmarkArtifactWriter.writeCompare(legacyToMainline);
                BenchmarkArtifactWriter.writeCompare(mainlineToShowcase);

                legacyRuns.add(legacy);
                mainlineRuns.add(mainline);
                showcaseRuns.add(showcase);
                legacyToMainlineRuns.add(legacyToMainline);
                mainlineToShowcaseRuns.add(mainlineToShowcase);

                System.out.println("  Seed " + seed + ":");
                System.out.println("    Legacy   : " + legacy.toSummary());
                System.out.println("    Mainline : " + mainline.toSummary());
                System.out.println("    Showcase : " + showcase.toSummary());
                System.out.println("    Legacy->Mainline : " + legacyToMainline.toSummary());
                System.out.println("    Mainline->Showcase: " + mainlineToShowcase.toSummary());
            }

            System.out.println("  Mean Legacy   : " + formatAveragedReport(legacyRuns));
            System.out.println("  Mean Mainline : " + formatAveragedReport(mainlineRuns));
            System.out.println("  Mean Showcase : " + formatAveragedReport(showcaseRuns));
            System.out.println("  Mean Legacy->Mainline : " + formatAveragedCompare(legacyToMainlineRuns));
            System.out.println("  Mean Mainline->Showcase: " + formatAveragedCompare(mainlineToShowcaseRuns));
            System.out.println("  Best Mainline seed  : " + bestSeedSummary(mainlineRuns));
            System.out.println("  Worst Mainline seed : " + worstSeedSummary(mainlineRuns));
            System.out.println("  Best Showcase seed  : " + bestSeedSummary(showcaseRuns));
            System.out.println("  Worst Showcase seed : " + worstSeedSummary(showcaseRuns));
        }

        System.out.println("=================================================");
    }

    private static void runStressTuneBatch() {
        System.out.println("=================================================");
        System.out.println("   ROUTECHAIN AI - RECOVERY MULTI-SEED BATCH");
        System.out.println("=================================================");

        for (ScenarioConfig scenario : STRESS_SCENARIOS) {
            List<RunReport> legacyRuns = new java.util.ArrayList<>();
            List<RunReport> omegaRuns = new java.util.ArrayList<>();
            List<ReplayCompareResult> compares = new java.util.ArrayList<>();

            System.out.println();
            System.out.println("Scenario: " + scenario.name());
            for (long seed : DEV_TUNING_SEEDS) {
                RunReport legacy = runScenario(
                        scenario,
                        SimulationEngine.DispatchMode.LEGACY,
                        OmegaDispatchAgent.AblationMode.FULL,
                        OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC,
                        seed);
                RunReport omega = runScenario(
                        scenario,
                        SimulationEngine.DispatchMode.OMEGA,
                        OmegaDispatchAgent.AblationMode.FULL,
                        OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC,
                        seed);
                ReplayCompareResult compare = ReplayCompareResult.compare(legacy, omega);
                BenchmarkArtifactWriter.writeCompare(compare);
                legacyRuns.add(legacy);
                omegaRuns.add(omega);
                compares.add(compare);
                System.out.println("  Seed " + seed + ": " + compare.toSummary());
            }

            System.out.println("  Mean Legacy : " + formatAveragedReport(legacyRuns));
            System.out.println("  Mean Omega  : " + formatAveragedReport(omegaRuns));
            System.out.println("  Mean Delta  : " + formatAveragedCompare(compares));
            System.out.println("  Best Omega seed  : " + bestSeedSummary(omegaRuns));
            System.out.println("  Worst Omega seed : " + worstSeedSummary(omegaRuns));
        }

        System.out.println("=================================================");
    }

    private static RunReport runScenario(
            ScenarioConfig scenario,
            SimulationEngine.DispatchMode mode) {
        return runScenario(
                scenario,
                mode,
                OmegaDispatchAgent.AblationMode.FULL,
                OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC,
                42L);
    }

    private static RunReport runScenario(
            ScenarioConfig scenario,
            SimulationEngine.DispatchMode mode,
            OmegaDispatchAgent.AblationMode ablationMode) {
        return runScenario(
                scenario,
                mode,
                ablationMode,
                OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC,
                42L);
    }

    private static RunReport runScenario(
            ScenarioConfig scenario,
            SimulationEngine.DispatchMode mode,
            OmegaDispatchAgent.AblationMode ablationMode,
            OmegaDispatchAgent.ExecutionProfile executionProfile) {
        return runScenario(scenario, mode, ablationMode, executionProfile, 42L);
    }

    private static RunReport runScenario(
            ScenarioConfig scenario,
            SimulationEngine.DispatchMode mode,
            OmegaDispatchAgent.AblationMode ablationMode,
            OmegaDispatchAgent.ExecutionProfile executionProfile,
            long seed) {

        SimulationEngine engine = new SimulationEngine(seed);
        engine.setDispatchMode(mode);
        engine.getOmegaAgent().setDiagnosticLoggingEnabled(false);
        engine.setOmegaAblationMode(ablationMode);
        engine.setExecutionProfile(executionProfile);
        engine.setInitialDriverCount(scenario.drivers());
        engine.setDemandMultiplier(scenario.demandMultiplier());
        engine.setTrafficIntensity(scenario.trafficIntensity());
        engine.setWeatherProfile(scenario.weatherProfile());
        scenario.setup().configure(engine.getShockEngine());

        for (int i = 0; i < scenario.ticks(); i++) {
            engine.tickHeadless();
        }

        RunReport report = engine.createRunReport(
                scenario.name() + "-" + mode.name().toLowerCase()
                        + "-" + ablationMode.name().toLowerCase()
                        + "-" + executionProfile.name().toLowerCase()
                        + "-seed" + seed,
                seed
        );
        BenchmarkArtifactWriter.writeRun(report);
        return report;
    }

    private static String formatFocusMetrics(RunReport report) {
        return String.format(
                "3plus=%.1f%% corridor=%.2f goodLast=%.1f%% emptyKm=%.2f nextIdle=%.2fm zigzag=%.2f",
                report.visibleBundleThreePlusRate(),
                report.deliveryCorridorQuality(),
                report.lastDropGoodZoneRate(),
                report.expectedPostCompletionEmptyKm(),
                report.nextOrderIdleMinutes(),
                report.zigZagPenaltyAvg());
    }

    private static String formatRouteDelta(RunReport baseline, RunReport candidate) {
        return String.format(
                "3plus=%+.1fpp corridor=%+.2f goodLast=%+.1fpp emptyKm=%+.2f nextIdle=%+.2fm zigzag=%+.2f",
                candidate.visibleBundleThreePlusRate() - baseline.visibleBundleThreePlusRate(),
                candidate.deliveryCorridorQuality() - baseline.deliveryCorridorQuality(),
                candidate.lastDropGoodZoneRate() - baseline.lastDropGoodZoneRate(),
                candidate.expectedPostCompletionEmptyKm() - baseline.expectedPostCompletionEmptyKm(),
                candidate.nextOrderIdleMinutes() - baseline.nextOrderIdleMinutes(),
                candidate.zigZagPenaltyAvg() - baseline.zigZagPenaltyAvg());
    }

    private static String formatAveragedReport(List<RunReport> runs) {
        int n = Math.max(1, runs.size());
        double completion = 0.0;
        double onTime = 0.0;
        double cancel = 0.0;
        double deadhead = 0.0;
        double bundleThreePlus = 0.0;
        double corridor = 0.0;
        double goodLast = 0.0;
        double emptyKm = 0.0;
        double nextIdle = 0.0;
        double zigZag = 0.0;
        double maxBundle = 0.0;
        double realAssign = 0.0;
        double cleanSubThree = 0.0;
        double waitThree = 0.0;
        double launchThree = 0.0;
        double downgrade = 0.0;
        double augment = 0.0;
        double holdOnly = 0.0;
        for (RunReport run : runs) {
            completion += run.completionRate();
            onTime += run.onTimeRate();
            cancel += run.cancellationRate();
            deadhead += run.deadheadDistanceRatio();
            bundleThreePlus += run.visibleBundleThreePlusRate();
            corridor += run.deliveryCorridorQuality();
            goodLast += run.lastDropGoodZoneRate();
            emptyKm += run.expectedPostCompletionEmptyKm();
            nextIdle += run.nextOrderIdleMinutes();
            zigZag += run.zigZagPenaltyAvg();
            maxBundle += run.maxObservedBundleSize();
            realAssign += run.realAssignmentRate();
            cleanSubThree += run.selectedSubThreeRateInCleanRegime();
            waitThree += run.waveAssemblyWaitRate();
            launchThree += run.thirdOrderLaunchRate();
            downgrade += run.stressDowngradeRate();
            augment += run.prePickupAugmentRate();
            holdOnly += run.holdOnlySelectionRate();
        }
        return String.format(
                "completion=%.1f%% onTime=%.1f%% cancel=%.1f%% deadhead=%.1f%% | 3plus=%.1f%% corridor=%.2f goodLast=%.1f%% emptyKm=%.2f nextIdle=%.2fm zigzag=%.2f maxBundle=%.2f | realAssign=%.1f%% cleanSub3=%.1f%% wait3=%.1f%% launch3=%.1f%% downgrade=%.1f%% augment=%.1f%% holdOnly=%.1f%%",
                completion / n, onTime / n, cancel / n, deadhead / n,
                bundleThreePlus / n, corridor / n, goodLast / n,
                emptyKm / n, nextIdle / n, zigZag / n, maxBundle / n,
                realAssign / n, cleanSubThree / n, waitThree / n, launchThree / n, downgrade / n,
                augment / n, holdOnly / n);
    }

    private static String formatAveragedCompare(List<ReplayCompareResult> compares) {
        int n = Math.max(1, compares.size());
        double gain = 0.0;
        double completion = 0.0;
        double onTime = 0.0;
        double cancel = 0.0;
        double deadhead = 0.0;
        double bundleThreePlus = 0.0;
        double corridor = 0.0;
        double goodLast = 0.0;
        double emptyKm = 0.0;
        double realAssign = 0.0;
        double waitThree = 0.0;
        double launchThree = 0.0;
        double recoverThree = 0.0;
        double subThree = 0.0;
        double downgrade = 0.0;
        int aiBetter = 0;
        int mixed = 0;
        int baselineBetter = 0;
        for (ReplayCompareResult compare : compares) {
            gain += compare.overallGainPercent();
            completion += compare.completionRateDelta();
            onTime += compare.onTimeRateDelta();
            cancel += compare.cancellationRateDelta();
            deadhead += compare.deadheadRatioDelta();
            bundleThreePlus += compare.visibleBundleThreePlusRateDelta();
            corridor += compare.deliveryCorridorQualityDelta();
            goodLast += compare.lastDropGoodZoneRateDelta();
            emptyKm += compare.expectedPostCompletionEmptyKmDelta();
            realAssign += compare.realAssignmentRateDelta();
            waitThree += compare.waveAssemblyWaitRateDelta();
            launchThree += compare.thirdOrderLaunchRateDelta();
            recoverThree += compare.cleanWaveRecoveryRateDelta();
            subThree += compare.selectedSubThreeRateInCleanRegimeDelta();
            downgrade += compare.stressDowngradeRateDelta();
            switch (compare.verdict()) {
                case "AI_BETTER" -> aiBetter++;
                case "BASELINE_BETTER" -> baselineBetter++;
                default -> mixed++;
            }
        }
        return String.format(
                "gain=%.1f%% completion=%+.1f%% onTime=%+.1f%% cancel=%+.1f%% deadhead=%+.1f%% | 3plus=%+.1fpp corridor=%+.2f goodLast=%+.1fpp emptyKm=%+.2f | realAssign=%+.1fpp wait3=%+.1fpp launch3=%+.1fpp recover3=%+.1fpp sub3=%+.1fpp downgrade=%+.1fpp | verdicts A=%d M=%d B=%d",
                gain / n, completion / n, onTime / n, cancel / n, deadhead / n,
                bundleThreePlus / n, corridor / n, goodLast / n, emptyKm / n,
                realAssign / n, waitThree / n, launchThree / n, recoverThree / n, subThree / n, downgrade / n,
                aiBetter, mixed, baselineBetter);
    }

    private static String bestSeedSummary(List<RunReport> runs) {
        RunReport best = runs.stream()
                .max(java.util.Comparator.comparingDouble(ScenarioBatchRunner::showcaseStrengthScore))
                .orElseThrow();
        return String.format(
                "seed=%d score=%.2f completion=%.1f%% onTime=%.1f%% 3plus=%.1f%% corridor=%.2f goodLast=%.1f%% emptyKm=%.2f",
                best.seed(), showcaseStrengthScore(best), best.completionRate(),
                best.onTimeRate(), best.visibleBundleThreePlusRate(),
                best.deliveryCorridorQuality(), best.lastDropGoodZoneRate(),
                best.expectedPostCompletionEmptyKm());
    }

    private static String worstSeedSummary(List<RunReport> runs) {
        RunReport worst = runs.stream()
                .min(java.util.Comparator.comparingDouble(ScenarioBatchRunner::showcaseStrengthScore))
                .orElseThrow();
        return String.format(
                "seed=%d score=%.2f completion=%.1f%% onTime=%.1f%% 3plus=%.1f%% corridor=%.2f goodLast=%.1f%% emptyKm=%.2f",
                worst.seed(), showcaseStrengthScore(worst), worst.completionRate(),
                worst.onTimeRate(), worst.visibleBundleThreePlusRate(),
                worst.deliveryCorridorQuality(), worst.lastDropGoodZoneRate(),
                worst.expectedPostCompletionEmptyKm());
    }

    private static double showcaseStrengthScore(RunReport run) {
        return run.completionRate() * 0.32
                + run.onTimeRate() * 0.18
                - run.cancellationRate() * 0.14
                - run.deadheadDistanceRatio() * 0.14
                + run.visibleBundleThreePlusRate() * 0.08
                + run.deliveryCorridorQuality() * 12.0 * 0.08
                + run.lastDropGoodZoneRate() * 0.04
                - run.expectedPostCompletionEmptyKm() * 2.0 * 0.02
                - run.nextOrderIdleMinutes() * 0.01
                - run.zigZagPenaltyAvg() * 8.0 * 0.01;
    }

    private static void configureShowcasePickupWave(ScenarioShockEngine shocks) {
        shocks.triggerLocalBurst(List.of("q1", "q3"), 2.2, 60, 780, 240.0);
        shocks.triggerMultiZoneWave(
                List.of("q1", "q3", "bt"),
                List.of(0L, 18L, 36L),
                1.35, 150, 600, 180.0);
    }

    private static void configureMerchantClusterWave(ScenarioShockEngine shocks) {
        shocks.triggerLocalBurst(List.of("q1"), 2.8, 30, 840, 260.0);
        shocks.triggerLocalBurst(List.of("q3"), 1.9, 60, 720, 220.0);
        shocks.triggerMultiZoneWave(
                List.of("q1", "q3", "bt"),
                List.of(0L, 12L, 24L),
                1.20, 180, 540, 170.0);
    }

    private static void configureSoftLandingCorridor(ScenarioShockEngine shocks) {
        shocks.triggerMultiZoneWave(
                List.of("q10", "q3", "q1", "bt"),
                List.of(0L, 18L, 36L, 54L),
                1.55, 120, 660, 220.0);
        shocks.triggerLocalBurst(List.of("bt"), 1.55, 330, 480, 190.0);
        shocks.triggerLocalBurst(List.of("q1"), 1.10, 90, 360, 140.0);
    }
}
