package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.ai.BanditPosteriorSnapshot;
import com.routechain.ai.OmegaDispatchAgent;
import com.routechain.domain.Enums.WeatherProfile;
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
 * Runs a small, fixed-seed demo pack that emphasizes visibly intelligent route decisions.
 */
public final class RouteIntelligenceDemoProofRunner {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path ROOT = Path.of("build", "routechain-apex", "benchmarks");
    private static final Path CERTIFICATION_DIR = ROOT.resolve("certification");
    private static final Path PROOF_CASE_DIR = CERTIFICATION_DIR.resolve("proof-cases");
    private static final Path DEMO_CSV = ROOT.resolve("route_intelligence_demo_proof.csv");
    private static final Path SMOKE_VERDICT_PATH = CERTIFICATION_DIR.resolve("route-intelligence-verdict-smoke.json");
    private static final long DEMO_SEED = 42L;
    private static final String BASELINE_POLICY = "Legacy";
    private static final String ADAPTIVE_POLICY = "Omega-current";
    private static final String STATIC_POLICY = "Omega-static-weight";
    private static final BanditPosteriorSnapshot DEMO_ADAPTIVE_PRIOR = new BanditPosteriorSnapshot(
            -0.28,
            0.22,
            0.18,
            -0.26,
            -0.30,
            0.30,
            0.22,
            -0.14,
            0.72,
            0.62,
            0.60,
            0.76,
            0.74,
            0.72,
            0.62,
            0.56,
            0L,
            0L
    );

    private RouteIntelligenceDemoProofRunner() {}

    public static List<ProofCaseDescriptor> listProofCases() {
        return curatedCaseDefinitions().stream()
                .map(definition -> new ProofCaseDescriptor(
                        definition.caseId(),
                        definition.title(),
                        definition.regimeName(),
                        definition.scenarioName(),
                        definition.kpiLens(),
                        true,
                        true))
                .toList();
    }

    public static ProofCaseExecution runProofCase(String caseId, String mode) {
        CuratedCaseDefinition definition = findCase(caseId);
        String normalizedMode = normalizeMode(mode);
        ProofExecutionBundle bundle = executeCase(definition, normalizedMode, "live".equals(normalizedMode));
        return toExecution(definition, normalizedMode, bundle);
    }

    public static void main(String[] args) {
        String laneName = args.length > 0 && !args[0].isBlank()
                ? args[0].trim().toLowerCase(Locale.ROOT)
                : "demo";
        List<DemoProofCase> cases = List.of(
                runCounterfactualCase("CLEAR", "instant-normal", 1200, 40, 0.85, 0.30, WeatherProfile.CLEAR),
                runCounterfactualCase("RUSH_HOUR", "instant-rush_hour", 1200, 40, 1.15, 0.58, WeatherProfile.CLEAR),
                runCounterfactualCase("SHORTAGE", "post_drop_shortage", 1200, 26, 1.05, 0.42, WeatherProfile.CLEAR),
                runRealisticDinnerPeakCase()
        );
        int regimesPassed = (int) cases.stream().filter(DemoProofCase::overallPass).count();
        int adaptiveBeatsStaticCount = (int) cases.stream().filter(DemoProofCase::adaptiveBeatsStatic).count();
        boolean smokeNonRegressionPass = smokeLaneStillHealthy();
        List<String> notes = new ArrayList<>();
        if (regimesPassed < 3) {
            notes.add("fewer than three curated demo regimes passed");
        }
        if (adaptiveBeatsStaticCount < 2) {
            notes.add("adaptive route does not beat the static-weight comparator on enough regimes");
        }
        if (!smokeNonRegressionPass) {
            notes.add("smoke verdict missing or regressed");
        }
        RouteIntelligenceDemoProofSummary summary = new RouteIntelligenceDemoProofSummary(
                BenchmarkSchema.VERSION,
                "route-intelligence-demo-proof-" + laneName,
                Instant.now(),
                BenchmarkCertificationSupport.resolveGitRevision(),
                BASELINE_POLICY,
                ADAPTIVE_POLICY,
                STATIC_POLICY,
                cases,
                regimesPassed,
                adaptiveBeatsStaticCount,
                smokeNonRegressionPass,
                smokeNonRegressionPass && regimesPassed >= 3 && adaptiveBeatsStaticCount >= 2,
                notes
        );
        writeSummary(summary);
        System.out.println("[RouteIntelligenceDemoProof] lane=" + laneName
                + " overallPass=" + summary.overallPass()
                + " regimesPassed=" + summary.regimesPassed()
                + " adaptiveBeatsStatic=" + summary.adaptiveBeatsStaticCount());
        if (!summary.overallPass()) {
            throw new IllegalStateException("Route intelligence demo proof failed for lane " + laneName);
        }
    }

    static DemoProofCase evaluateCase(String regimeName,
                                      String scenarioName,
                                      long seed,
                                      ReplayCompareResult baselineToAdaptive,
                                      ReplayCompareResult staticToAdaptive) {
        boolean adaptiveBeatsBaseline = switch (regimeName) {
            case "CLEAR" -> baselineToAdaptive.completionRateDelta() >= 1.0
                    && baselineToAdaptive.deadheadPerCompletedOrderKmDelta() <= -0.10
                    && baselineToAdaptive.cancellationRateDelta() <= 0.0;
            case "RUSH_HOUR" -> baselineToAdaptive.completionRateDelta() >= 1.0
                    && baselineToAdaptive.deadheadPerCompletedOrderKmDelta() <= -0.10
                    && baselineToAdaptive.cancellationRateDelta() <= 0.0;
            case "SHORTAGE" -> baselineToAdaptive.completionRateDelta() >= 0.5
                    && baselineToAdaptive.deadheadPerCompletedOrderKmDelta() <= -0.10
                    && baselineToAdaptive.postDropOrderHitRateDelta() > 0.25
                    && baselineToAdaptive.cancellationRateDelta() <= 0.0
                    && baselineToAdaptive.realAssignmentRateDelta() >= -2.0
                    && baselineToAdaptive.borrowedDeadheadPerExecutedOrderKmDelta() <= 1.75;
            case "DINNER_PEAK_HCMC" -> baselineToAdaptive.completionRateDelta() >= 0.5
                    && baselineToAdaptive.deadheadPerCompletedOrderKmDelta() <= -0.10
                    && baselineToAdaptive.postDropOrderHitRateDelta() > 3.0
                    && baselineToAdaptive.cancellationRateDelta() <= 0.0;
            default -> baselineToAdaptive.overallGainPercent() > 0.0;
        };
        boolean adaptiveBeatsStatic = switch (regimeName) {
            case "CLEAR" -> staticToAdaptive.overallGainPercent() >= 0.25
                    || (staticToAdaptive.completionRateDelta() >= 0.75
                    && staticToAdaptive.deadheadPerCompletedOrderKmDelta() <= -0.05
                    && staticToAdaptive.cancellationRateDelta() <= 0.0
                    && staticToAdaptive.postDropOrderHitRateDelta() > 0.5);
            case "RUSH_HOUR" -> staticToAdaptive.completionRateDelta() >= 1.0
                    && staticToAdaptive.deadheadPerCompletedOrderKmDelta() <= -0.10
                    && staticToAdaptive.cancellationRateDelta() <= 0.0;
            case "SHORTAGE" -> staticToAdaptive.overallGainPercent() >= 0.75
                    || (staticToAdaptive.completionRateDelta() >= 0.75
                    && staticToAdaptive.deadheadPerCompletedOrderKmDelta() <= -0.10
                    && staticToAdaptive.cancellationRateDelta() <= 0.0
                    && staticToAdaptive.postDropOrderHitRateDelta() > 0.75);
            case "DINNER_PEAK_HCMC" -> staticToAdaptive.overallGainPercent() >= -0.10
                    && staticToAdaptive.completionRateDelta() >= 0.25
                    && staticToAdaptive.postDropOrderHitRateDelta() > 0.5
                    && staticToAdaptive.cancellationRateDelta() <= 0.0;
            default -> staticToAdaptive.overallGainPercent() > 0.5
                    || (staticToAdaptive.deadheadPerCompletedOrderKmDelta() < 0.0
                    && staticToAdaptive.postDropOrderHitRateDelta() > 0.0);
        };
        boolean baselineOnlyPass = baselineToAdaptive.overallGainPercent() >= 0.75
                && baselineToAdaptive.completionRateDelta() >= 1.0
                && baselineToAdaptive.deadheadPerCompletedOrderKmDelta() <= -0.20
                && baselineToAdaptive.cancellationRateDelta() <= 0.0;
        List<String> notes = new ArrayList<>();
        if (!adaptiveBeatsBaseline) {
            notes.add("adaptive route does not clear the curated baseline bar for " + regimeName);
        }
        if (!adaptiveBeatsStatic) {
            notes.add("adaptive route does not beat the static-weight comparator strongly enough");
        }
        if (adaptiveBeatsBaseline && !adaptiveBeatsStatic && baselineOnlyPass) {
            notes.add("regime qualifies as a baseline-only demo pass; static-weight still remains a stronger comparator");
        }
        return new DemoProofCase(
                regimeName,
                scenarioName,
                seed,
                BASELINE_POLICY,
                ADAPTIVE_POLICY,
                STATIC_POLICY,
                baselineToAdaptive,
                staticToAdaptive,
                explain(regimeName, baselineToAdaptive, staticToAdaptive),
                adaptiveBeatsBaseline,
                adaptiveBeatsStatic,
                adaptiveBeatsBaseline && (adaptiveBeatsStatic || baselineOnlyPass),
                notes
        );
    }

    static DemoDecisionExplanation explain(String regimeName,
                                           ReplayCompareResult baselineToAdaptive,
                                           ReplayCompareResult staticToAdaptive) {
        String acceptedBatchBecause = baselineToAdaptive.bundleRateDelta() > 0.0
                ? "batch-2 rate increased while deadhead per completed order improved by "
                + formatSigned(baselineToAdaptive.deadheadPerCompletedOrderKmDelta()) + " km"
                : "";
        String rejectedBatchBecause = baselineToAdaptive.bundleRateDelta() <= 0.0
                || baselineToAdaptive.holdOnlySelectionRateDelta() > 0.0
                ? "system rejected weak batching and kept execution tighter under " + regimeName
                : "";
        String preferredLandingBecause = baselineToAdaptive.lastDropGoodZoneRateDelta() > 0.0
                || baselineToAdaptive.deliveryCorridorQualityDelta() > 0.0
                ? "landing quality improved by "
                + formatSigned(baselineToAdaptive.lastDropGoodZoneRateDelta()) + " pp on last-drop quality"
                : "";
        String oracleDisagreed = staticToAdaptive.overallGainPercent() > 0.5
                ? "adaptive scorer stayed ahead of the static-weight route by "
                + formatSigned(staticToAdaptive.overallGainPercent()) + " gain points"
                : "";
        String futureOpportunityBonus = baselineToAdaptive.postDropOrderHitRateDelta() > 0.0
                ? "post-drop hit improved by "
                + formatSigned(baselineToAdaptive.postDropOrderHitRateDelta()) + " pp and empty km moved "
                + formatSigned(baselineToAdaptive.expectedPostCompletionEmptyKmDelta()) + " km"
                : "";
        String summaryParagraph = buildSummaryParagraph(
                regimeName,
                baselineToAdaptive,
                staticToAdaptive,
                acceptedBatchBecause,
                rejectedBatchBecause,
                preferredLandingBecause,
                futureOpportunityBonus);
        return new DemoDecisionExplanation(
                acceptedBatchBecause,
                rejectedBatchBecause,
                preferredLandingBecause,
                oracleDisagreed,
                futureOpportunityBonus,
                summaryParagraph
        );
    }

    private static String buildSummaryParagraph(String regimeName,
                                                ReplayCompareResult baselineToAdaptive,
                                                ReplayCompareResult staticToAdaptive,
                                                String acceptedBatchBecause,
                                                String rejectedBatchBecause,
                                                String preferredLandingBecause,
                                                String futureOpportunityBonus) {
        List<String> parts = new ArrayList<>();
        parts.add("In " + regimeName + ", adaptive Omega moved overall gain by "
                + formatSigned(baselineToAdaptive.overallGainPercent())
                + " against Legacy and by "
                + formatSigned(staticToAdaptive.overallGainPercent())
                + " against the static-weight comparator.");
        if (!acceptedBatchBecause.isBlank()) {
            parts.add(acceptedBatchBecause + ".");
        }
        if (!rejectedBatchBecause.isBlank()) {
            parts.add(rejectedBatchBecause + ".");
        }
        if (!preferredLandingBecause.isBlank()) {
            parts.add(preferredLandingBecause + ".");
        }
        if (!futureOpportunityBonus.isBlank()) {
            parts.add(futureOpportunityBonus + ".");
        }
        return String.join(" ", parts);
    }

    private static DemoProofCase runCounterfactualCase(String regimeName,
                                                       String scenarioName,
                                                       int ticks,
                                                       int drivers,
                                                       double demandMultiplier,
                                                       double trafficIntensity,
                                                       WeatherProfile weatherProfile) {
        return runCase(regimeName, new DemoScenarioSpec(
                scenarioName,
                9,
                0,
                ticks,
                drivers,
                demandMultiplier,
                trafficIntensity,
                weatherProfile,
                shockEngine -> { }
        ));
    }

    private static DemoProofCase runRealisticDinnerPeakCase() {
        RealisticScenarioGenerator generator = new RealisticScenarioGenerator();
        RealisticScenarioGenerator.RealisticScenarioSpec dinnerSpec = generator.generate(1, DEMO_SEED).stream()
                .filter(spec -> spec.bucket() == RealisticScenarioGenerator.ScenarioBucket.DINNER_PEAK)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing dinner peak realistic demo spec"));
        return runCase("DINNER_PEAK_HCMC", new DemoScenarioSpec(
                dinnerSpec.scenarioName(),
                dinnerSpec.startHour(),
                dinnerSpec.startMinute(),
                dinnerSpec.ticks(),
                dinnerSpec.drivers(),
                dinnerSpec.demandMultiplier(),
                dinnerSpec.trafficIntensity(),
                dinnerSpec.weatherProfile(),
                shockEngine -> generator.configureShocks(dinnerSpec, shockEngine)
        ));
    }

    private static DemoProofCase runCase(String regimeName, DemoScenarioSpec scenario) {
        ProofExecutionBundle bundle = executeScenarioBundle(regimeName, scenario, true);
        return bundle.demoCase();
    }

    private static ProofExecutionBundle executeCase(CuratedCaseDefinition definition,
                                                    String mode,
                                                    boolean persistArtifacts) {
        ProofExecutionBundle bundle = executeScenarioBundle(definition.regimeName(), definition.scenario(), persistArtifacts);
        if (persistArtifacts) {
            writeProofCaseExecution(definition, mode, bundle);
        }
        return bundle;
    }

    private static ProofExecutionBundle executeScenarioBundle(String regimeName,
                                                              DemoScenarioSpec scenario,
                                                              boolean persistArtifacts) {
        BanditPosteriorSnapshot beforeSnapshot = DEMO_ADAPTIVE_PRIOR;
        RunReport baseline = runScenario(regimeName, scenario, BASELINE_POLICY,
                SimulationEngine.DispatchMode.LEGACY, OmegaDispatchAgent.AblationMode.FULL);
        RunReport adaptive = runScenario(regimeName, scenario, ADAPTIVE_POLICY,
                SimulationEngine.DispatchMode.OMEGA, OmegaDispatchAgent.AblationMode.FULL);
        RunReport staticWeight = runScenario(regimeName, scenario, STATIC_POLICY,
                SimulationEngine.DispatchMode.OMEGA, OmegaDispatchAgent.AblationMode.NO_ADAPTIVE_BANDIT);
        if (persistArtifacts) {
            BenchmarkArtifactWriter.writeRun(baseline);
            BenchmarkArtifactWriter.writeRun(adaptive);
            BenchmarkArtifactWriter.writeRun(staticWeight);
        }
        ReplayCompareResult baselineToAdaptive = ReplayCompareResult.compare(baseline, adaptive);
        ReplayCompareResult staticToAdaptive = ReplayCompareResult.compare(staticWeight, adaptive);
        if (persistArtifacts) {
            BenchmarkArtifactWriter.writeCompare(baselineToAdaptive);
            BenchmarkArtifactWriter.writeCompare(staticToAdaptive);
        }
        DemoProofCase demoCase =
                evaluateCase(regimeName, scenario.scenarioName(), DEMO_SEED, baselineToAdaptive, staticToAdaptive);
        BanditPosteriorSnapshot afterSnapshot = PlatformRuntimeBootstrap.getModelArtifactProvider()
                .banditPosteriorSnapshot("route-utility-bandit");
        OrToolsShadowPolicySearch oracleSearch = new OrToolsShadowPolicySearch();
        return new ProofExecutionBundle(
                demoCase,
                baseline,
                adaptive,
                staticWeight,
                beforeSnapshot,
                afterSnapshot == null ? beforeSnapshot : afterSnapshot,
                oracleSearch.mode(),
                oracleSearch.objective(baseline),
                oracleSearch.objective(adaptive),
                oracleSearch.objective(staticWeight)
        );
    }

    private static RunReport runScenario(String regimeName,
                                         DemoScenarioSpec scenario,
                                         String policyName,
                                         SimulationEngine.DispatchMode dispatchMode,
                                         OmegaDispatchAgent.AblationMode ablationMode) {
        SimulationEngine engine = new SimulationEngine(DEMO_SEED);
        engine.setDispatchMode(dispatchMode);
        engine.setOmegaAblationMode(ablationMode);
        engine.setExecutionProfile(OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);
        engine.getOmegaAgent().reset();
        if (dispatchMode == SimulationEngine.DispatchMode.OMEGA
                && ablationMode == OmegaDispatchAgent.AblationMode.FULL) {
            engine.getOmegaAgent().loadBanditPosterior(DEMO_ADAPTIVE_PRIOR);
        }
        engine.getOmegaAgent().setDiagnosticLoggingEnabled(false);
        engine.setInitialDriverCount(scenario.drivers());
        engine.setDemandMultiplier(scenario.demandMultiplier());
        engine.setTrafficIntensity(scenario.trafficIntensity());
        engine.setWeatherProfile(scenario.weatherProfile());
        engine.setSimulationStartTime(scenario.startHour(), scenario.startMinute());
        scenario.setup().configure(engine.getShockEngine());
        for (int i = 0; i < scenario.ticks(); i++) {
            engine.tickHeadless();
        }
        return engine.createRunReport(
                "demo-" + BenchmarkCertificationSupport.normalize(regimeName) + "-"
                        + BenchmarkCertificationSupport.normalize(policyName) + "-seed" + DEMO_SEED,
                DEMO_SEED
        );
    }

    private static boolean smokeLaneStillHealthy() {
        try {
            if (Files.notExists(SMOKE_VERDICT_PATH)) {
                return false;
            }
            RouteIntelligenceVerdictSummary summary = GSON.fromJson(
                    Files.readString(SMOKE_VERDICT_PATH, StandardCharsets.UTF_8),
                    RouteIntelligenceVerdictSummary.class
            );
            return summary != null
                    && "YES".equalsIgnoreCase(summary.aiVerdict())
                    && !"NO".equalsIgnoreCase(summary.routingVerdict());
        } catch (IOException e) {
            return false;
        }
    }

    private static void writeSummary(RouteIntelligenceDemoProofSummary summary) {
        try {
            Files.createDirectories(CERTIFICATION_DIR);
            Path jsonPath = CERTIFICATION_DIR.resolve(summary.laneName() + ".json");
            Path markdownPath = CERTIFICATION_DIR.resolve(summary.laneName() + ".md");
            Files.writeString(jsonPath, GSON.toJson(summary), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(markdownPath, renderMarkdown(summary), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            appendCsv(summary);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write route intelligence demo proof summary", e);
        }
    }

    private static void appendCsv(RouteIntelligenceDemoProofSummary summary) throws IOException {
        boolean fileExists = Files.exists(DEMO_CSV);
        Files.createDirectories(DEMO_CSV.getParent());
        if (!fileExists) {
            Files.writeString(DEMO_CSV,
                    "laneName,generatedAt,gitRevision,regimesPassed,adaptiveBeatsStaticCount,smokeNonRegressionPass,overallPass"
                            + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
        Files.writeString(DEMO_CSV,
                String.format(Locale.ROOT, "%s,%s,%s,%d,%d,%s,%s%n",
                        safe(summary.laneName()),
                        safe(String.valueOf(summary.generatedAt())),
                        safe(summary.gitRevision()),
                        summary.regimesPassed(),
                        summary.adaptiveBeatsStaticCount(),
                        Boolean.toString(summary.smokeNonRegressionPass()),
                        Boolean.toString(summary.overallPass())),
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
    }

    private static String renderMarkdown(RouteIntelligenceDemoProofSummary summary) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Route Intelligence Demo Proof").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("- Lane: ").append(summary.laneName()).append(System.lineSeparator());
        builder.append("- Overall Pass: ").append(summary.overallPass()).append(System.lineSeparator());
        builder.append("- Regimes Passed: ").append(summary.regimesPassed()).append("/").append(summary.cases().size()).append(System.lineSeparator());
        builder.append("- Adaptive Beats Static: ").append(summary.adaptiveBeatsStaticCount()).append(System.lineSeparator());
        builder.append("- Smoke Non Regression: ").append(summary.smokeNonRegressionPass()).append(System.lineSeparator());
        builder.append(System.lineSeparator()).append("## Curated Cases").append(System.lineSeparator());
        for (DemoProofCase demoCase : summary.cases()) {
            builder.append(System.lineSeparator()).append("### ").append(demoCase.regimeName()).append(System.lineSeparator());
            builder.append("- Scenario: ").append(demoCase.scenarioName()).append(System.lineSeparator());
            builder.append("- Adaptive vs Legacy Gain: ")
                    .append(formatSigned(demoCase.baselineToAdaptive().overallGainPercent())).append(System.lineSeparator());
            builder.append("- Adaptive vs Static Gain: ")
                    .append(formatSigned(demoCase.staticToAdaptive().overallGainPercent())).append(System.lineSeparator());
            builder.append("- Pass: ").append(demoCase.overallPass()).append(System.lineSeparator());
            builder.append("- Explanation: ").append(demoCase.explanation().summaryParagraph()).append(System.lineSeparator());
            if (!demoCase.notes().isEmpty()) {
                builder.append("- Notes: ").append(String.join("; ", demoCase.notes())).append(System.lineSeparator());
            }
        }
        if (!summary.notes().isEmpty()) {
            builder.append(System.lineSeparator()).append("## Notes").append(System.lineSeparator());
            for (String note : summary.notes()) {
                builder.append("- ").append(note).append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(',', ';').replace('\n', ' ').replace('\r', ' ');
    }

    private static String formatSigned(double value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }

    private static List<CuratedCaseDefinition> curatedCaseDefinitions() {
        return List.of(
                new CuratedCaseDefinition(
                        "clear-smart-batch-win",
                        "CLEAR smart batch win",
                        "CLEAR",
                        "instant-normal",
                        "Completion, deadhead, and batch-2 quality",
                        new DemoScenarioSpec(
                                "instant-normal",
                                9,
                                0,
                                1200,
                                40,
                                0.85,
                                0.30,
                                WeatherProfile.CLEAR,
                                shockEngine -> { }
                        )),
                new CuratedCaseDefinition(
                        "rush-hour-reject-bad-batch",
                        "RUSH_HOUR reject bad batch",
                        "RUSH_HOUR",
                        "instant-rush_hour",
                        "Rush-hour safety, deadhead rejection, and SLA guard",
                        new DemoScenarioSpec(
                                "instant-rush_hour",
                                9,
                                0,
                                1200,
                                40,
                                1.15,
                                0.58,
                                WeatherProfile.CLEAR,
                                shockEngine -> { }
                        )),
                new CuratedCaseDefinition(
                        "shortage-controlled-borrow",
                        "SHORTAGE controlled borrow",
                        "SHORTAGE",
                        "post_drop_shortage",
                        "Borrow control, landing quality, and shortage recovery",
                        new DemoScenarioSpec(
                                "post_drop_shortage",
                                9,
                                0,
                                1200,
                                26,
                                1.05,
                                0.42,
                                WeatherProfile.CLEAR,
                                shockEngine -> { }
                        )),
                new CuratedCaseDefinition(
                        "dinner-peak-hcmc-landing-win",
                        "DINNER_PEAK_HCMC landing win",
                        "DINNER_PEAK_HCMC",
                        "realistic-hcmc-dinner-peak-run0",
                        "Post-drop hit, landing quality, and city-state opportunity",
                        dinnerPeakScenario())
        );
    }

    private static CuratedCaseDefinition findCase(String caseId) {
        return curatedCaseDefinitions().stream()
                .filter(definition -> definition.caseId().equalsIgnoreCase(caseId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown proof case: " + caseId));
    }

    private static DemoScenarioSpec dinnerPeakScenario() {
        RealisticScenarioGenerator generator = new RealisticScenarioGenerator();
        RealisticScenarioGenerator.RealisticScenarioSpec dinnerSpec = generator.generate(1, DEMO_SEED).stream()
                .filter(spec -> spec.bucket() == RealisticScenarioGenerator.ScenarioBucket.DINNER_PEAK)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing dinner peak realistic demo spec"));
        return new DemoScenarioSpec(
                dinnerSpec.scenarioName(),
                dinnerSpec.startHour(),
                dinnerSpec.startMinute(),
                dinnerSpec.ticks(),
                dinnerSpec.drivers(),
                dinnerSpec.demandMultiplier(),
                dinnerSpec.trafficIntensity(),
                dinnerSpec.weatherProfile(),
                shockEngine -> generator.configureShocks(dinnerSpec, shockEngine)
        );
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "shadow";
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        return "live".equals(normalized) ? "live" : "shadow";
    }

    private static ProofCaseExecution toExecution(CuratedCaseDefinition definition,
                                                  String mode,
                                                  ProofExecutionBundle bundle) {
        List<PolicyRunSummary> policies = List.of(
                toPolicyRunSummary("legacy", BASELINE_POLICY, bundle.baseline(), bundle.oracleBaselineObjective()),
                toPolicyRunSummary("adaptive", ADAPTIVE_POLICY, bundle.adaptive(), bundle.oracleAdaptiveObjective()),
                toPolicyRunSummary("static", STATIC_POLICY, bundle.staticWeight(), bundle.oracleStaticObjective())
        );
        double bestOracleObjective = Math.max(
                bundle.oracleBaselineObjective(),
                Math.max(bundle.oracleAdaptiveObjective(), bundle.oracleStaticObjective()));
        String leaderPolicyKey = bestOracleObjective == bundle.oracleAdaptiveObjective()
                ? "adaptive"
                : bestOracleObjective == bundle.oracleStaticObjective() ? "static" : "legacy";
        return new ProofCaseExecution(
                definition.caseId(),
                definition.title(),
                mode,
                definition.regimeName(),
                definition.kpiLens(),
                bundle.demoCase().scenarioName(),
                bundle.demoCase().overallPass(),
                bundle.demoCase().adaptiveBeatsBaseline(),
                bundle.demoCase().adaptiveBeatsStatic(),
                toReplayLens(bundle.demoCase().baselineToAdaptive()),
                toReplayLens(bundle.demoCase().staticToAdaptive()),
                policies,
                new OracleSubsetReference(
                        bundle.oracleMode(),
                        leaderPolicyKey,
                        bundle.oracleAdaptiveObjective() - bestOracleObjective,
                        bundle.oracleBaselineObjective(),
                        bundle.oracleAdaptiveObjective(),
                        bundle.oracleStaticObjective()),
                new AdaptiveLearningSnapshotView(
                        bundle.beforeSnapshot(),
                        bundle.afterSnapshot(),
                        new AdaptiveLearningDelta(
                                bundle.afterSnapshot().pickupCostWeight() - bundle.beforeSnapshot().pickupCostWeight(),
                                bundle.afterSnapshot().batchSynergyWeight() - bundle.beforeSnapshot().batchSynergyWeight(),
                                bundle.afterSnapshot().dropCoherenceWeight() - bundle.beforeSnapshot().dropCoherenceWeight(),
                                bundle.afterSnapshot().slaRiskWeight() - bundle.beforeSnapshot().slaRiskWeight(),
                                bundle.afterSnapshot().deadheadPenaltyWeight() - bundle.beforeSnapshot().deadheadPenaltyWeight(),
                                bundle.afterSnapshot().futureOpportunityWeight() - bundle.beforeSnapshot().futureOpportunityWeight(),
                                bundle.afterSnapshot().positioningValueWeight() - bundle.beforeSnapshot().positioningValueWeight(),
                                bundle.afterSnapshot().stressPenaltyWeight() - bundle.beforeSnapshot().stressPenaltyWeight(),
                                bundle.afterSnapshot().updateCount() - bundle.beforeSnapshot().updateCount(),
                                bundle.afterSnapshot().checkpointVersion() - bundle.beforeSnapshot().checkpointVersion()
                        )),
                bundle.demoCase().explanation().summaryParagraph(),
                new ExplanationView(
                        bundle.demoCase().explanation().acceptedBatchBecause(),
                        bundle.demoCase().explanation().rejectedBatchBecause(),
                        bundle.demoCase().explanation().preferredLandingBecause(),
                        bundle.demoCase().explanation().oracleDisagreed(),
                        bundle.demoCase().explanation().futureOpportunityBonus(),
                        bundle.demoCase().explanation().summaryParagraph()),
                bundle.demoCase().notes()
        );
    }

    private static PolicyRunSummary toPolicyRunSummary(String policyKey,
                                                       String policyLabel,
                                                       RunReport report,
                                                       double oracleObjective) {
        return new PolicyRunSummary(
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

    private static ReplayLens toReplayLens(ReplayCompareResult compare) {
        return new ReplayLens(
                compare.verdict(),
                compare.overallGainPercent(),
                compare.completionRateDelta(),
                compare.deadheadPerCompletedOrderKmDelta(),
                compare.cancellationRateDelta(),
                compare.postDropOrderHitRateDelta(),
                compare.nextOrderIdleMinutesDelta(),
                compare.lastDropGoodZoneRateDelta(),
                compare.bundleRateDelta()
        );
    }

    private static void writeProofCaseExecution(CuratedCaseDefinition definition,
                                                String mode,
                                                ProofExecutionBundle bundle) {
        try {
            Files.createDirectories(PROOF_CASE_DIR);
            ProofCaseExecution execution = toExecution(definition, mode, bundle);
            Path jsonPath = PROOF_CASE_DIR.resolve(definition.caseId() + "-" + mode + ".json");
            Files.writeString(jsonPath, GSON.toJson(execution), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to persist proof case execution for " + definition.caseId(), e);
        }
    }

    private record DemoScenarioSpec(
            String scenarioName,
            int startHour,
            int startMinute,
            int ticks,
            int drivers,
            double demandMultiplier,
            double trafficIntensity,
            WeatherProfile weatherProfile,
            ScenarioSetup setup
    ) { }

    @FunctionalInterface
    private interface ScenarioSetup {
        void configure(ScenarioShockEngine shockEngine);
    }

    private record CuratedCaseDefinition(
            String caseId,
            String title,
            String regimeName,
            String scenarioName,
            String kpiLens,
            DemoScenarioSpec scenario
    ) {}

    private record ProofExecutionBundle(
            DemoProofCase demoCase,
            RunReport baseline,
            RunReport adaptive,
            RunReport staticWeight,
            BanditPosteriorSnapshot beforeSnapshot,
            BanditPosteriorSnapshot afterSnapshot,
            String oracleMode,
            double oracleBaselineObjective,
            double oracleAdaptiveObjective,
            double oracleStaticObjective
    ) {}

    public record ProofCaseDescriptor(
            String caseId,
            String title,
            String regimeName,
            String scenarioName,
            String kpiLens,
            boolean liveSupported,
            boolean shadowSupported
    ) {}

    public record ProofCaseExecution(
            String caseId,
            String title,
            String mode,
            String regimeName,
            String kpiLens,
            String scenarioName,
            boolean overallPass,
            boolean adaptiveBeatsBaseline,
            boolean adaptiveBeatsStatic,
            ReplayLens adaptiveVsLegacy,
            ReplayLens adaptiveVsStatic,
            List<PolicyRunSummary> policies,
            OracleSubsetReference oracleSubset,
            AdaptiveLearningSnapshotView adaptiveLearning,
            String explanationSummary,
            ExplanationView explanation,
            List<String> notes
    ) {}

    public record ExplanationView(
            String acceptedBatchBecause,
            String rejectedBatchBecause,
            String preferredLandingBecause,
            String oracleDisagreed,
            String futureOpportunityBonus,
            String summaryParagraph
    ) {}

    public record ReplayLens(
            String verdict,
            double overallGainPercent,
            double completionRateDelta,
            double deadheadPerCompletedOrderKmDelta,
            double cancellationRateDelta,
            double postDropOrderHitRateDelta,
            double nextOrderIdleMinutesDelta,
            double lastDropGoodZoneRateDelta,
            double bundleRateDelta
    ) {}

    public record PolicyRunSummary(
            String policyKey,
            String policyLabel,
            String runId,
            String scenarioName,
            double completionRate,
            double deadheadPerCompletedOrderKm,
            double cancellationRate,
            double postDropOrderHitRate,
            double nextOrderIdleMinutes,
            double lastDropGoodZoneRate,
            double bundleRate,
            double oracleObjective
    ) {}

    public record OracleSubsetReference(
            String mode,
            String leaderPolicyKey,
            double adaptiveGapToLeader,
            double legacyObjective,
            double adaptiveObjective,
            double staticObjective
    ) {}

    public record AdaptiveLearningSnapshotView(
            BanditPosteriorSnapshot before,
            BanditPosteriorSnapshot after,
            AdaptiveLearningDelta delta
    ) {}

    public record AdaptiveLearningDelta(
            double pickupCostWeightDelta,
            double batchSynergyWeightDelta,
            double dropCoherenceWeightDelta,
            double slaRiskWeightDelta,
            double deadheadPenaltyWeightDelta,
            double futureOpportunityWeightDelta,
            double positioningValueWeightDelta,
            double stressPenaltyWeightDelta,
            long updateCountDelta,
            long checkpointVersionDelta
    ) {}
}

record DemoDecisionExplanation(
        String acceptedBatchBecause,
        String rejectedBatchBecause,
        String preferredLandingBecause,
        String oracleDisagreed,
        String futureOpportunityBonus,
        String summaryParagraph
) {}

record DemoProofCase(
        String regimeName,
        String scenarioName,
        long seed,
        String baselinePolicy,
        String adaptivePolicy,
        String staticPolicy,
        ReplayCompareResult baselineToAdaptive,
        ReplayCompareResult staticToAdaptive,
        DemoDecisionExplanation explanation,
        boolean adaptiveBeatsBaseline,
        boolean adaptiveBeatsStatic,
        boolean overallPass,
        List<String> notes
) {}

record RouteIntelligenceDemoProofSummary(
        String schemaVersion,
        String laneName,
        Instant generatedAt,
        String gitRevision,
        String baselinePolicy,
        String adaptivePolicy,
        String staticPolicy,
        List<DemoProofCase> cases,
        int regimesPassed,
        int adaptiveBeatsStaticCount,
        boolean smokeNonRegressionPass,
        boolean overallPass,
        List<String> notes
) {}
