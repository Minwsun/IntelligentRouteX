package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.infra.GsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregates route, simulation, and benchmark artifacts into one repo-level certification verdict.
 */
public final class RepoIntelligenceCertificationRunner {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path ROOT = Path.of("build", "routechain-apex", "benchmarks");
    private static final Path RUNS_DIR = ROOT.resolve("runs");
    private static final Path COMPARES_DIR = ROOT.resolve("compares");
    private static final Path STATS_DIR = ROOT.resolve("stats");
    private static final Path CERTIFICATION_DIR = ROOT.resolve("certification");
    private static final Path ROUTE_AI_SMOKE_JSON = CERTIFICATION_DIR.resolve("route-ai-certification-smoke.json");
    private static final Path ENVIRONMENT_JSON = ROOT.resolve("environment_manifest.json");
    private static final Path RUNTIME_SLO_JSON = ROOT.resolve("runtime_slo_summary.json");
    private static final Path MEMORY_GC_JSON = ROOT.resolve("memory_gc_summary.json");
    private static final Path REPO_CERTIFICATION_CSV = ROOT.resolve("repo_intelligence_certification.csv");

    private RepoIntelligenceCertificationRunner() {}

    public static void main(String[] args) {
        String laneName = args.length > 0 && !args[0].isBlank()
                ? args[0].trim().toLowerCase()
                : "smoke";
        BenchmarkCertificationScenarioMatrix.LaneDefinition lane =
                BenchmarkCertificationConfigLoader.loadScenarioMatrix().lane(laneName);
        BenchmarkCertificationBaseline baseline = BenchmarkCertificationConfigLoader.loadBaseline();
        BenchmarkEnvironmentProfile environment = readOptionalJson(ENVIRONMENT_JSON, BenchmarkEnvironmentProfile.class);
        RuntimeSloSummary runtime = readRequiredJson(RUNTIME_SLO_JSON, RuntimeSloSummary.class, "runtime SLO summary");
        RouteAiCertificationSummary routeSmoke = readOptionalJson(ROUTE_AI_SMOKE_JSON, RouteAiCertificationSummary.class);
        MemoryGcSummary soakSummary = readOptionalJson(MEMORY_GC_JSON, MemoryGcSummary.class);
        List<RunReport> runs = readJsonDirectory(RUNS_DIR, RunReport.class);
        List<ReplayCompareResult> compares = readJsonDirectory(COMPARES_DIR, ReplayCompareResult.class);
        List<ScenarioGroupCertificationResult> scenarioGroups = evaluateScenarioGroups(lane, baseline, runs);

        CertificationGateResult correctnessGate = evaluateCorrectnessGate(routeSmoke, runs);
        CertificationGateResult latencyGate = evaluateLatencyGate(runtime, scenarioGroups);
        CertificationGateResult routeQualityGate = new CertificationGateResult(
                "Route Quality",
                scenarioGroups.stream().allMatch(ScenarioGroupCertificationResult::routeQualityPass),
                collectScenarioNotes(scenarioGroups, ScenarioGroupCertificationResult::routeQualityPass, "route"));
        CertificationGateResult continuityGate = new CertificationGateResult(
                "Continuity",
                scenarioGroups.stream().allMatch(ScenarioGroupCertificationResult::continuityPass),
                collectScenarioNotes(scenarioGroups, ScenarioGroupCertificationResult::continuityPass, "continuity"));
        CertificationGateResult stressSafetyGate = new CertificationGateResult(
                "Stress/Safety",
                scenarioGroups.stream().allMatch(ScenarioGroupCertificationResult::stressSafetyPass),
                collectScenarioNotes(scenarioGroups, ScenarioGroupCertificationResult::stressSafetyPass, "stress"));
        CertificationGateResult auxiliaryGate = evaluateAuxiliaryGate(laneName, soakSummary);
        LegacyReferenceResult legacyReference = evaluateLegacyReference(lane, compares);

        boolean overallPass = correctnessGate.pass()
                && latencyGate.pass()
                && routeQualityGate.pass()
                && continuityGate.pass()
                && stressSafetyGate.pass()
                && auxiliaryGate.pass();

        List<String> notes = new ArrayList<>();
        notes.add("lane=" + laneName
                + " seeds=" + lane.seeds().stream().map(String::valueOf).collect(Collectors.joining("|"))
                + " groups=" + lane.scenarioBuckets().stream()
                .map(BenchmarkCertificationScenarioMatrix.ScenarioBucket::scenarioGroup)
                .collect(Collectors.joining("|")));
        if (legacyReference.warning()) {
            notes.add("legacy warning: consecutive underperformance count="
                    + legacyReference.consecutiveUnderperformCount());
        }

        RepoIntelligenceCertificationSummary summary = new RepoIntelligenceCertificationSummary(
                BenchmarkSchema.VERSION,
                "repo-intelligence-" + laneName,
                Instant.now(),
                BenchmarkCertificationSupport.resolveGitRevision(),
                System.getProperty("java.version", "unknown"),
                environment == null ? runtime.profileName() : environment.profileName(),
                lane.seeds(),
                lane.scenarioBuckets().stream()
                        .map(BenchmarkCertificationScenarioMatrix.ScenarioBucket::scenarioGroup)
                        .toList(),
                correctnessGate,
                latencyGate,
                routeQualityGate,
                continuityGate,
                stressSafetyGate,
                auxiliaryGate,
                legacyReference,
                scenarioGroups,
                overallPass,
                overallPass ? (legacyReference.warning() ? "PASS_WITH_WARNING" : "PASS") : "FAIL",
                notes
        );

        BenchmarkArtifactWriter.writeRepoIntelligenceCertificationSummary(summary);
        System.out.println("[RepoIntelligence] lane=" + laneName
                + " verdict=" + summary.overallVerdict()
                + " correctness=" + correctnessGate.pass()
                + " latency=" + latencyGate.pass()
                + " route=" + routeQualityGate.pass()
                + " continuity=" + continuityGate.pass()
                + " stress=" + stressSafetyGate.pass()
                + " auxiliary=" + auxiliaryGate.pass());
        if (!summary.overallPass()) {
            throw new IllegalStateException("Repo intelligence certification failed for lane " + laneName);
        }
    }

    private static CertificationGateResult evaluateCorrectnessGate(RouteAiCertificationSummary routeSmoke,
                                                                    List<RunReport> runs) {
        List<String> notes = new ArrayList<>();
        boolean hasCurrentOmegaRun = runs.stream().anyMatch(BenchmarkCertificationSupport::isCurrentOmegaRun);
        if (!hasCurrentOmegaRun) {
            notes.add("missing current Omega run artifacts");
        }
        if (routeSmoke == null) {
            notes.add("missing route-ai-certification-smoke summary");
        } else if (!routeSmoke.overallPass()) {
            notes.add("route-ai-certification-smoke did not pass");
        }
        boolean pass = hasCurrentOmegaRun && routeSmoke != null && routeSmoke.routeRegressionPass() && routeSmoke.overallPass();
        return new CertificationGateResult("Correctness", pass, notes);
    }

    private static CertificationGateResult evaluateLatencyGate(RuntimeSloSummary runtime,
                                                               List<ScenarioGroupCertificationResult> scenarioGroups) {
        List<String> notes = new ArrayList<>();
        if (!runtime.measurementValid()) {
            notes.add("runtime measurement is not valid");
        }
        notes.add("runtime dispatchP95=" + String.format("%.1f", runtime.dispatchP95Ms())
                + "ms dispatchP99=" + String.format("%.1f", runtime.dispatchP99Ms()) + "ms");
        scenarioGroups.stream()
                .filter(group -> group.dispatchP95Ms() <= 0.0 || group.dispatchP99Ms() <= 0.0)
                .forEach(group -> notes.add(group.scenarioGroup() + ": missing latency samples"));
        scenarioGroups.stream()
                .filter(group -> !group.notes().stream().noneMatch(note -> note.startsWith("latency:")))
                .forEach(group -> notes.addAll(group.notes().stream()
                        .filter(note -> note.startsWith("latency:"))
                        .toList()));
        boolean perGroupPass = scenarioGroups.stream().noneMatch(group ->
                group.notes().stream().anyMatch(note -> note.startsWith("latency:")));
        boolean pass = runtime.measurementValid()
                && perGroupPass;
        return new CertificationGateResult("Latency", pass, notes);
    }

    private static CertificationGateResult evaluateAuxiliaryGate(String laneName, MemoryGcSummary soakSummary) {
        List<String> notes = new ArrayList<>();
        boolean pass = true;
        if ("smoke".equals(laneName)) {
            Path microLatency = ROOT.resolve("latency").resolve("micro_omega-hotpath.json");
            if (Files.notExists(microLatency)) {
                pass = false;
                notes.add("missing micro hot-path latency artifact");
            }
        } else if ("certification".equals(laneName)) {
            BenchmarkStatSummary trackAGain = readOptionalJson(
                    STATS_DIR.resolve("trackA_all-overallGainPercent.json"),
                    BenchmarkStatSummary.class);
            if (trackAGain == null || trackAGain.sampleCount() <= 0) {
                pass = false;
                notes.add("missing trackA global gain summary");
            }
        } else if ("nightly".equals(laneName)) {
            BenchmarkStatSummary trackBGain = readOptionalJson(
                    STATS_DIR.resolve("trackB_all-overallGainPercent.json"),
                    BenchmarkStatSummary.class);
            if (trackBGain == null || trackBGain.sampleCount() <= 0) {
                pass = false;
                notes.add("missing trackB global gain summary");
            } else if (trackBGain.mean() < -1.0) {
                pass = false;
                notes.add("trackB overall gain regressed below nightly tolerance");
            }
            if (soakSummary == null) {
                pass = false;
                notes.add("missing soak summary");
            } else if (!soakSummary.memoryGrowthPass()) {
                pass = false;
                notes.add("soak memory growth failed");
            }
        }
        return new CertificationGateResult("Auxiliary", pass, notes);
    }

    private static LegacyReferenceResult evaluateLegacyReference(
            BenchmarkCertificationScenarioMatrix.LaneDefinition lane,
            List<ReplayCompareResult> compares) {
        List<ReplayCompareResult> matched = compares.stream()
                .filter(BenchmarkCertificationSupport::isCurrentOmegaCompare)
                .filter(compare -> lane.scenarioBuckets().stream()
                        .anyMatch(bucket -> BenchmarkCertificationSupport.matchesScenario(
                                compare.scenarioA(), bucket.scenarioMatchers())))
                .toList();
        if (matched.isEmpty()) {
            return new LegacyReferenceResult(true, 0, 0.0, 0.0, 0.0,
                    List.of("missing Legacy vs Omega compare artifacts"));
        }

        double gain = BenchmarkCertificationSupport.average(
                matched.stream().map(ReplayCompareResult::overallGainPercent).toList());
        double completion = BenchmarkCertificationSupport.average(
                matched.stream().map(ReplayCompareResult::completionRateDelta).toList());
        double deadhead = BenchmarkCertificationSupport.average(
                matched.stream().map(ReplayCompareResult::deadheadRatioDelta).toList());
        boolean currentUnderperform = gain < 0.0 || completion < 0.0 || deadhead > 0.0;

        int consecutive = currentUnderperform ? 1 : 0;
        if (currentUnderperform) {
            LegacyHistoryEntry previous = readPreviousLegacyHistory(lane.laneName());
            if (previous != null && previous.legacyUnderperforming()) {
                consecutive = previous.consecutiveUnderperformCount() + 1;
            }
        }

        List<String> notes = new ArrayList<>();
        notes.add("legacy ref gain=" + String.format("%.2f", gain)
                + " completion=" + String.format("%+.2f", completion)
                + " deadhead=" + String.format("%+.2f", deadhead));
        boolean warning = currentUnderperform && consecutive >= 2;
        if (warning) {
            notes.add("Omega-current underperformed Legacy in two consecutive lane runs");
        }
        return new LegacyReferenceResult(warning, consecutive, gain, completion, deadhead, notes);
    }

    private static LegacyHistoryEntry readPreviousLegacyHistory(String laneName) {
        try {
            if (Files.notExists(REPO_CERTIFICATION_CSV)) {
                return null;
            }
            List<String> lines = Files.readAllLines(REPO_CERTIFICATION_CSV, StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 1; i--) {
                if (lines.get(i).isBlank()) {
                    continue;
                }
                String[] values = lines.get(i).split(",", -1);
                if (values.length < 14) {
                    continue;
                }
                if (!values[0].equals("repo-intelligence-" + laneName)) {
                    continue;
                }
                return new LegacyHistoryEntry(
                        Boolean.parseBoolean(values[11]),
                        Integer.parseInt(values[12])
                );
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private static List<ScenarioGroupCertificationResult> evaluateScenarioGroups(
            BenchmarkCertificationScenarioMatrix.LaneDefinition lane,
            BenchmarkCertificationBaseline baseline,
            List<RunReport> runs) {
        List<RunReport> currentOmegaRuns = runs.stream()
                .filter(BenchmarkCertificationSupport::isCurrentOmegaRun)
                .toList();
        List<ScenarioGroupCertificationResult> results = new ArrayList<>();
        for (BenchmarkCertificationScenarioMatrix.ScenarioBucket bucket : lane.scenarioBuckets()) {
            List<RunReport> matched = currentOmegaRuns.stream()
                    .filter(run -> BenchmarkCertificationSupport.matchesScenario(
                            run.scenarioName(), bucket.scenarioMatchers()))
                    .toList();
            BenchmarkCertificationBaseline.ScenarioGroupThresholds thresholds =
                    baseline.thresholdsFor(bucket.scenarioGroup());
            if (matched.isEmpty()) {
                results.add(new ScenarioGroupCertificationResult(
                        bucket.scenarioGroup(),
                        0,
                        List.of(),
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
                        false,
                        false,
                        false,
                        List.of("missing current Omega runs for scenario group " + bucket.scenarioGroup())
                ));
                continue;
            }

            List<Long> observedSeeds = matched.stream()
                    .map(RunReport::seed)
                    .distinct()
                    .sorted()
                    .toList();
            boolean hasExpectedSeeds = observedSeeds.containsAll(lane.seeds());
            double dispatchP95 = averageMetric(matched, run -> run.latency().dispatchP95Ms());
            double dispatchP99 = averageMetric(matched, run -> run.latency().dispatchP99Ms());
            double completionRate = averageMetric(matched, RunReport::completionRate);
            double onTimeRate = averageMetric(matched, RunReport::onTimeRate);
            double cancellationRate = averageMetric(matched, RunReport::cancellationRate);
            double failedOrderRate = averageMetric(matched, RunReport::failedOrderRate);
            double realAssignmentRate = averageMetric(matched, RunReport::realAssignmentRate);
            double deadheadRatio = averageMetric(matched, RunReport::deadheadDistanceRatio);
            double deadheadPerCompleted = averageMetric(matched, RunReport::deadheadPerCompletedOrderKm);
            double postDropHitRate = averageMetric(matched, RunReport::postDropOrderHitRate);
            double corridorQuality = averageMetric(matched, RunReport::deliveryCorridorQuality);
            double goodLastRate = averageMetric(matched, RunReport::lastDropGoodZoneRate);
            double zigZagPenalty = averageMetric(matched, RunReport::zigZagPenaltyAvg);
            double avgAssignedDeadhead = averageMetric(matched, RunReport::avgAssignedDeadheadKm);
            double fallbackShare = averageMetric(matched, RunReport::fallbackDirect);
            double borrowedShare = averageMetric(matched, RunReport::borrowedExec);
            double selectedSubThree = averageMetric(matched, RunReport::selectedSubThreeRateInCleanRegime);
            double stressDowngrade = averageMetric(matched, RunReport::stressDowngradeRate);
            double nextIdle = averageMetric(matched, RunReport::nextOrderIdleMinutes);
            double expectedEmptyKm = averageMetric(matched, RunReport::expectedPostCompletionEmptyKm);

            List<String> notes = new ArrayList<>();
            if (!hasExpectedSeeds) {
                notes.add("missing expected seeds: expected="
                        + joinLongs(lane.seeds()) + " observed=" + joinLongs(observedSeeds));
            }
            if (dispatchP95 > thresholds.maxDispatchP95Ms()) {
                notes.add("latency: dispatchP95 above " + thresholds.maxDispatchP95Ms() + "ms");
            }
            if (dispatchP99 > thresholds.maxDispatchP99Ms()) {
                notes.add("latency: dispatchP99 above " + thresholds.maxDispatchP99Ms() + "ms");
            }

            boolean routeQualityPass = hasExpectedSeeds
                    && completionRate >= thresholds.minCompletionRate()
                    && onTimeRate >= thresholds.minOnTimeRate()
                    && realAssignmentRate >= thresholds.minRealAssignmentRate()
                    && deadheadRatio <= thresholds.maxDeadheadDistanceRatio()
                    && deadheadPerCompleted <= thresholds.maxDeadheadDistancePerCompleted()
                    && corridorQuality >= thresholds.minDeliveryCorridorQuality()
                    && zigZagPenalty <= thresholds.maxZigZagPenalty()
                    && avgAssignedDeadhead <= thresholds.maxAverageAssignedDeadheadKm()
                    && fallbackShare <= thresholds.maxFallbackExecutedShare()
                    && borrowedShare <= thresholds.maxBorrowedCoverageExecutedShare()
                    && selectedSubThree <= thresholds.maxSelectedSubThreeInCleanRate();

            boolean continuityPass = hasExpectedSeeds
                    && postDropHitRate >= thresholds.minPostDropOrderHitRate()
                    && goodLastRate >= thresholds.minLastDropGoodZoneRate()
                    && (thresholds.maxNextOrderIdleMinutes() == null
                    || nextIdle <= thresholds.maxNextOrderIdleMinutes())
                    && (thresholds.maxExpectedPostCompletionEmptyKm() == null
                    || expectedEmptyKm <= thresholds.maxExpectedPostCompletionEmptyKm());

            boolean stressSafetyPass = hasExpectedSeeds
                    && stressDowngrade <= thresholds.maxStressDowngradeRate()
                    && cancellationRate <= thresholds.maxCancellationRate()
                    && failedOrderRate <= thresholds.maxFailedOrderRate()
                    && matched.stream().allMatch(run -> run.acceptance().safetyPass());

            results.add(new ScenarioGroupCertificationResult(
                    bucket.scenarioGroup(),
                    matched.size(),
                    observedSeeds,
                    dispatchP95,
                    dispatchP99,
                    completionRate,
                    onTimeRate,
                    cancellationRate,
                    failedOrderRate,
                    realAssignmentRate,
                    deadheadRatio,
                    deadheadPerCompleted,
                    postDropHitRate,
                    corridorQuality,
                    goodLastRate,
                    zigZagPenalty,
                    avgAssignedDeadhead,
                    fallbackShare,
                    borrowedShare,
                    selectedSubThree,
                    stressDowngrade,
                    nextIdle,
                    expectedEmptyKm,
                    routeQualityPass,
                    continuityPass,
                    stressSafetyPass,
                    notes
            ));
        }
        return results;
    }

    private static List<String> collectScenarioNotes(
            List<ScenarioGroupCertificationResult> groups,
            java.util.function.Predicate<ScenarioGroupCertificationResult> passPredicate,
            String label) {
        List<String> notes = new ArrayList<>();
        for (ScenarioGroupCertificationResult group : groups) {
            if (passPredicate.test(group)) {
                continue;
            }
            if (group.notes().isEmpty()) {
                notes.add(group.scenarioGroup() + ": " + label + " gate failed");
            } else {
                notes.add(group.scenarioGroup() + ": " + String.join("; ", group.notes()));
            }
        }
        return notes;
    }

    private static double averageMetric(List<RunReport> runs,
                                        java.util.function.ToDoubleFunction<RunReport> extractor) {
        List<Double> values = new ArrayList<>(runs.size());
        for (RunReport run : runs) {
            values.add(extractor.applyAsDouble(run));
        }
        return BenchmarkCertificationSupport.average(values);
    }

    private static <T> T readRequiredJson(Path path, Class<T> type, String label) {
        T value = readOptionalJson(path, type);
        if (value == null) {
            throw new IllegalStateException("Missing " + label + " at " + path);
        }
        return value;
    }

    private static <T> T readOptionalJson(Path path, Class<T> type) {
        try {
            if (Files.notExists(path)) {
                return null;
            }
            return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read json artifact " + path, e);
        }
    }

    private static <T> List<T> readJsonDirectory(Path dir, Class<T> type) {
        try {
            if (Files.notExists(dir)) {
                return List.of();
            }
            List<T> values = new ArrayList<>();
            try (var stream = Files.list(dir)) {
                for (Path path : stream.filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
                    values.add(GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type));
                }
            }
            return values;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read artifact directory " + dir, e);
        }
    }

    private static String joinLongs(List<Long> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining("|"));
    }

    private record LegacyHistoryEntry(
            boolean legacyUnderperforming,
            int consecutiveUnderperformCount
    ) {}
}
