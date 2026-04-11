package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.ai.OmegaDispatchAgent;
import com.routechain.infra.GsonSupport;
import com.routechain.infra.PlatformRuntimeBootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Validates whether the shared JavaFX SMART_DEMO_3x10 scenario is merely wired
 * correctly or also shows meaningful route intelligence versus baseline.
 */
public final class JavaFxSmartDemo3x10ValidationRunner {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path ROOT = Path.of("build", "routechain-apex", "benchmarks", "javafx");
    private static final Path JSON_PATH = ROOT.resolve("smart-demo-3x10-validation.json");
    private static final Path MD_PATH = ROOT.resolve("smart-demo-3x10-validation.md");
    private static final String LANE_NAME = "javafx-smart-demo-3x10-validation";
    private static final String LEGACY_POLICY = "Legacy";
    private static final String ADAPTIVE_POLICY = "Omega-current";
    private static final String STATIC_POLICY = "Omega-static-weight";

    private JavaFxSmartDemo3x10ValidationRunner() { }

    public static void main(String[] args) {
        SmartDemo3x10ValidationResult result = validateAndPersist();
        System.out.println("[JavaFxSmartDemo3x10Validation] verdict=" + result.verdict()
                + " adaptiveVsLegacy=" + formatSigned(result.adaptiveVsLegacy().overallGainPercent())
                + " adaptiveVsStatic=" + formatSigned(result.adaptiveVsStatic().overallGainPercent())
                + " behaviors=" + result.behaviorAssessment().satisfiedBehaviorCount());
    }

    public static SmartDemo3x10ValidationResult validateAndPersist() {
        SmartDemo3x10ValidationResult result = validate();
        writeArtifacts(result);
        return result;
    }

    static SmartDemo3x10ValidationResult validate() {
        JavaFxDemoScenarioSpec scenario = SmartDemo3x10Scenario.spec();
        PolicyExecution legacy = runScenario(LEGACY_POLICY, SimulationEngine.DispatchMode.LEGACY,
                OmegaDispatchAgent.AblationMode.FULL, scenario);
        PolicyExecution adaptive = runScenario(ADAPTIVE_POLICY, SimulationEngine.DispatchMode.OMEGA,
                OmegaDispatchAgent.AblationMode.FULL, scenario);
        PolicyExecution staticWeight = runScenario(STATIC_POLICY, SimulationEngine.DispatchMode.OMEGA,
                OmegaDispatchAgent.AblationMode.NO_ADAPTIVE_BANDIT, scenario);

        ReplayCompareResult adaptiveVsLegacy = ReplayCompareResult.compare(legacy.report(), adaptive.report());
        ReplayCompareResult adaptiveVsStatic = ReplayCompareResult.compare(staticWeight.report(), adaptive.report());
        OrToolsShadowPolicySearch oracleSearch = new OrToolsShadowPolicySearch();

        double legacyObjective = oracleSearch.objective(legacy.report());
        double adaptiveObjective = oracleSearch.objective(adaptive.report());
        double staticObjective = oracleSearch.objective(staticWeight.report());
        double bestObjective = Math.max(legacyObjective, Math.max(adaptiveObjective, staticObjective));
        String leaderPolicyKey = bestObjective == adaptiveObjective
                ? "adaptive"
                : bestObjective == staticObjective ? "static" : "legacy";

        SmartDemo3x10ValidationResult.RuntimeCorrectness runtimeCorrectness = new SmartDemo3x10ValidationResult.RuntimeCorrectness(
                adaptive.runtimePass(),
                scenario.driverCount(),
                adaptive.driverCountBeforeTicks(),
                scenario.orderCount(),
                adaptive.orderCountBeforeTicks(),
                adaptive.initialDriverCount(),
                adaptive.demandMultiplier(),
                adaptive.simulatedTime(),
                adaptive.weatherProfile()
        );

        boolean acceptedGoodBatch = adaptiveVsLegacy.bundleRateDelta() > 0.0
                && adaptiveVsLegacy.deadheadPerCompletedOrderKmDelta() < 0.0;
        boolean rejectedBadBatch = adaptiveVsLegacy.holdOnlySelectionRateDelta() <= 0.0
                && adaptiveVsLegacy.cancellationRateDelta() <= 0.0
                && adaptiveVsLegacy.avgAssignedDeadheadKmDelta() <= 0.0;
        boolean preferredBetterLanding = adaptiveVsLegacy.lastDropGoodZoneRateDelta() > 0.0
                || adaptiveVsLegacy.deliveryCorridorQualityDelta() > 0.0
                || adaptiveVsLegacy.postDropOrderHitRateDelta() > 0.0;
        boolean avoidedEmptyAfterDrop = adaptiveVsLegacy.expectedPostCompletionEmptyKmDelta() < 0.0
                || adaptiveVsLegacy.nextOrderIdleMinutesDelta() < 0.0;
        int satisfiedBehaviorCount = (acceptedGoodBatch ? 1 : 0)
                + (rejectedBadBatch ? 1 : 0)
                + (preferredBetterLanding ? 1 : 0)
                + (avoidedEmptyAfterDrop ? 1 : 0);

        SmartDemo3x10ValidationResult.BehaviorAssessment behaviorAssessment =
                new SmartDemo3x10ValidationResult.BehaviorAssessment(
                        acceptedGoodBatch,
                        rejectedBadBatch,
                        preferredBetterLanding,
                        avoidedEmptyAfterDrop,
                        satisfiedBehaviorCount
                );

        boolean adaptiveBeatsBaseline = adaptiveVsLegacy.completionRateDelta() >= 0.0
                && adaptiveVsLegacy.deadheadPerCompletedOrderKmDelta() < 0.0
                && adaptiveVsLegacy.cancellationRateDelta() <= 0.0
                && (adaptiveVsLegacy.postDropOrderHitRateDelta() > 0.0
                || adaptiveVsLegacy.nextOrderIdleMinutesDelta() < 0.0);
        boolean adaptiveBeatsStatic = adaptiveVsStatic.overallGainPercent() >= 0.25
                || (adaptiveVsStatic.completionRateDelta() >= 0.0
                && adaptiveVsStatic.cancellationRateDelta() <= 0.0
                && (adaptiveVsStatic.deadheadPerCompletedOrderKmDelta() < 0.0
                || adaptiveVsStatic.postDropOrderHitRateDelta() > 0.0));

        JavaFxDemoVerdict verdict;
        List<String> notes = new ArrayList<>();
        if (!runtimeCorrectness.pass()) {
            verdict = JavaFxDemoVerdict.RUNS_CORRECTLY_BUT_NOT_SMART_ENOUGH;
            notes.add("runtime correctness failed before intelligence scoring");
        } else if (adaptiveBeatsBaseline && adaptiveBeatsStatic && satisfiedBehaviorCount >= 2) {
            verdict = JavaFxDemoVerdict.SMART_DEMO_PASS;
        } else if (adaptiveBeatsBaseline && satisfiedBehaviorCount >= 2) {
            verdict = JavaFxDemoVerdict.BASELINE_ONLY_PASS;
            notes.add("adaptive clears the Legacy bar but does not separate strongly enough from static-weight");
        } else {
            verdict = JavaFxDemoVerdict.RUNS_CORRECTLY_BUT_NOT_SMART_ENOUGH;
            notes.add("adaptive does not clear the smart-demo bar on completion/deadhead/future value");
        }
        if (satisfiedBehaviorCount < 2) {
            notes.add("fewer than two smart-route behaviors are observable in the 3x10 case");
        }

        RouteIntelligenceDemoProofRunner.ExplanationView explanation =
                toExplanationView(adaptiveVsLegacy, adaptiveVsStatic);

        SmartDemo3x10ValidationResult.OracleAssessment oracleAssessment =
                new SmartDemo3x10ValidationResult.OracleAssessment(
                        oracleSearch.mode(),
                        leaderPolicyKey,
                        adaptiveObjective - bestObjective,
                        legacyObjective,
                        adaptiveObjective,
                        staticObjective,
                        adaptiveObjective >= bestObjective - 0.10
                                ? "ADAPTIVE_CLOSE_TO_ORACLE"
                                : "ADAPTIVE_FAR_FROM_ORACLE"
                );

        return new SmartDemo3x10ValidationResult(
                BenchmarkSchema.VERSION,
                LANE_NAME,
                Instant.now(),
                BenchmarkCertificationSupport.resolveGitRevision(),
                scenario,
                runtimeCorrectness,
                adaptiveVsLegacy,
                adaptiveVsStatic,
                List.of(
                        toPolicyComparison("legacy", LEGACY_POLICY, legacy.report(), legacyObjective),
                        toPolicyComparison("adaptive", ADAPTIVE_POLICY, adaptive.report(), adaptiveObjective),
                        toPolicyComparison("static", STATIC_POLICY, staticWeight.report(), staticObjective)
                ),
                oracleAssessment,
                behaviorAssessment,
                explanation.summaryParagraph(),
                verdict,
                notes
        );
    }

    private static PolicyExecution runScenario(String policyLabel,
                                               SimulationEngine.DispatchMode dispatchMode,
                                               OmegaDispatchAgent.AblationMode ablationMode,
                                               JavaFxDemoScenarioSpec scenario) {
        SimulationEngine engine = new SimulationEngine(scenario.seed());
        engine.setDispatchMode(dispatchMode);
        engine.setOmegaAblationMode(ablationMode);
        engine.setExecutionProfile(OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);
        engine.getOmegaAgent().reset();
        engine.getOmegaAgent().setDiagnosticLoggingEnabled(false);
        scenario.configureEngine(engine);
        engine.tickHeadless();
        scenario.injectManualEntities(engine);

        int driverCountBeforeTicks = engine.getDrivers().size();
        int orderCountBeforeTicks = engine.getActiveOrders().size();
        boolean runtimePass = driverCountBeforeTicks == scenario.driverCount()
                && orderCountBeforeTicks == scenario.orderCount()
                && engine.getInitialDriverCount() == scenario.initialDriverCount()
                && Math.abs(engine.getDemandMultiplier() - scenario.demandMultiplier()) < 1e-9
                && engine.getWeatherProfile() == scenario.weatherProfile()
                && engine.getSimulatedHour() == scenario.startHour()
                && engine.getSimulatedMinute() == scenario.startMinute();

        for (int i = 0; i < scenario.tickBudget(); i++) {
            engine.tickHeadless();
        }

        RunReport report = engine.createRunReport(scenario.scenarioName() + "-" + policyLabel.toLowerCase(Locale.ROOT), scenario.seed());
        engine.stop();
        return new PolicyExecution(
                report,
                runtimePass,
                driverCountBeforeTicks,
                orderCountBeforeTicks,
                engine.getInitialDriverCount(),
                engine.getDemandMultiplier(),
                engine.getSimulatedTimeFormatted(),
                engine.getWeatherProfile().name()
        );
    }

    private static RouteIntelligenceDemoProofRunner.ExplanationView toExplanationView(ReplayCompareResult adaptiveVsLegacy,
                                                                                      ReplayCompareResult adaptiveVsStatic) {
        var explanation = RouteIntelligenceDemoProofRunner.explain(
                "SMART_DEMO_3x10",
                adaptiveVsLegacy,
                adaptiveVsStatic
        );
        return new RouteIntelligenceDemoProofRunner.ExplanationView(
                explanation.acceptedBatchBecause(),
                explanation.rejectedBatchBecause(),
                explanation.preferredLandingBecause(),
                explanation.oracleDisagreed(),
                explanation.futureOpportunityBonus(),
                explanation.summaryParagraph()
        );
    }

    private static JavaFxPolicyComparison toPolicyComparison(String policyKey,
                                                             String policyLabel,
                                                             RunReport report,
                                                             double oracleObjective) {
        return new JavaFxPolicyComparison(
                policyKey,
                policyLabel,
                report.runId(),
                report.scenarioName(),
                report.completionRate(),
                report.deadheadPerCompletedOrderKm(),
                report.cancellationRate(),
                report.postDropOrderHitRate(),
                report.nextOrderIdleMinutes(),
                report.lastDropGoodZoneRate(),
                report.bundleRate(),
                oracleObjective
        );
    }

    private static void writeArtifacts(SmartDemo3x10ValidationResult result) {
        try {
            Files.createDirectories(ROOT);
            Files.writeString(JSON_PATH, GSON.toJson(result), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(MD_PATH, renderMarkdown(result), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to persist SMART_DEMO_3x10 validation artifacts", e);
        }
    }

    private static String renderMarkdown(SmartDemo3x10ValidationResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("# JavaFX SMART_DEMO_3x10 Validation").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("- Lane: ").append(result.laneName()).append(System.lineSeparator());
        builder.append("- Generated: ").append(result.generatedAt()).append(System.lineSeparator());
        builder.append("- Git SHA: ").append(result.gitRevision()).append(System.lineSeparator());
        builder.append("- Verdict: ").append(result.verdict()).append(System.lineSeparator());
        builder.append("- Scenario: ").append(result.scenario().scenarioName()).append(System.lineSeparator());
        builder.append("- Runtime correctness: ").append(result.runtimeCorrectness().pass()).append(System.lineSeparator());
        builder.append("- Drivers / Orders: ")
                .append(result.runtimeCorrectness().actualDrivers())
                .append("/")
                .append(result.runtimeCorrectness().expectedDrivers())
                .append(" drivers, ")
                .append(result.runtimeCorrectness().actualOrders())
                .append("/")
                .append(result.runtimeCorrectness().expectedOrders())
                .append(" orders")
                .append(System.lineSeparator());
        builder.append("- Oracle assessment: ").append(result.oracleAssessment().verdict()).append(System.lineSeparator());
        builder.append("- Observable smart behaviors: ").append(result.behaviorAssessment().satisfiedBehaviorCount())
                .append("/4")
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        builder.append("## Policy Compare").append(System.lineSeparator());
        for (JavaFxPolicyComparison policy : result.policies()) {
            builder.append("- ").append(policy.policyLabel())
                    .append(": completion=").append(String.format(Locale.ROOT, "%.2f%%", policy.completionRate()))
                    .append(", dh/completed=").append(String.format(Locale.ROOT, "%.2fkm", policy.deadheadPerCompletedOrderKm()))
                    .append(", cancel=").append(String.format(Locale.ROOT, "%.2f%%", policy.cancellationRate()))
                    .append(", postDropHit=").append(String.format(Locale.ROOT, "%.2f%%", policy.postDropOrderHitRate()))
                    .append(", oracle=").append(String.format(Locale.ROOT, "%.2f", policy.oracleObjective()))
                    .append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());

        builder.append("## Adaptive Compare").append(System.lineSeparator());
        builder.append("- Vs Legacy: gain=").append(formatSigned(result.adaptiveVsLegacy().overallGainPercent()))
                .append(", completion=").append(formatSigned(result.adaptiveVsLegacy().completionRateDelta()))
                .append(", deadhead/completed=").append(formatSigned(result.adaptiveVsLegacy().deadheadPerCompletedOrderKmDelta()))
                .append(", cancel=").append(formatSigned(result.adaptiveVsLegacy().cancellationRateDelta()))
                .append(", postDropHit=").append(formatSigned(result.adaptiveVsLegacy().postDropOrderHitRateDelta()))
                .append(System.lineSeparator());
        builder.append("- Vs Static: gain=").append(formatSigned(result.adaptiveVsStatic().overallGainPercent()))
                .append(", completion=").append(formatSigned(result.adaptiveVsStatic().completionRateDelta()))
                .append(", deadhead/completed=").append(formatSigned(result.adaptiveVsStatic().deadheadPerCompletedOrderKmDelta()))
                .append(", cancel=").append(formatSigned(result.adaptiveVsStatic().cancellationRateDelta()))
                .append(", postDropHit=").append(formatSigned(result.adaptiveVsStatic().postDropOrderHitRateDelta()))
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        builder.append("## Smart Behaviors").append(System.lineSeparator());
        builder.append("- acceptedGoodBatch: ").append(result.behaviorAssessment().acceptedGoodBatch()).append(System.lineSeparator());
        builder.append("- rejectedBadBatch: ").append(result.behaviorAssessment().rejectedBadBatch()).append(System.lineSeparator());
        builder.append("- preferredBetterLanding: ").append(result.behaviorAssessment().preferredBetterLanding()).append(System.lineSeparator());
        builder.append("- avoidedEmptyAfterDrop: ").append(result.behaviorAssessment().avoidedEmptyAfterDrop()).append(System.lineSeparator())
                .append(System.lineSeparator());

        builder.append("## Explanation").append(System.lineSeparator());
        builder.append(result.explanationSummary()).append(System.lineSeparator());
        if (!result.notes().isEmpty()) {
            builder.append(System.lineSeparator()).append("## Notes").append(System.lineSeparator());
            for (String note : result.notes()) {
                builder.append("- ").append(note).append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    private static String formatSigned(double value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }

    private record PolicyExecution(
            RunReport report,
            boolean runtimePass,
            int driverCountBeforeTicks,
            int orderCountBeforeTicks,
            int initialDriverCount,
            double demandMultiplier,
            String simulatedTime,
            String weatherProfile
    ) { }
}
