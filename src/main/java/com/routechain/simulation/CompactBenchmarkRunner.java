package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.core.CompactDecisionExplanation;
import com.routechain.core.CompactPlanType;
import com.routechain.core.WeightSnapshot;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.infra.GsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

public final class CompactBenchmarkRunner {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path OUTPUT_DIR = Path.of("build", "routechain-apex", "benchmarks", "compact");

    private CompactBenchmarkRunner() {
    }

    public static void main(String[] args) {
        CompactBenchmarkLane lane = CompactBenchmarkLane.fromArg(args.length == 0 ? null : args[0]);
        CompactBenchmarkSummary summary = runBenchmark(lane);
        writeArtifacts(summary);
        System.out.println(renderMarkdown(summary));
    }

    public static CompactBenchmarkSummary runBenchmark(CompactBenchmarkLane lane) {
        return runBenchmark(lane, lane.seeds());
    }

    public static CompactBenchmarkSummary runBenchmark(List<Long> seeds) {
        return runBenchmark(CompactBenchmarkLane.SMOKE, seeds);
    }

    public static CompactBenchmarkSummary runBenchmark(CompactBenchmarkLane lane, List<Long> seeds) {
        List<CompactBenchmarkCase> cases = new ArrayList<>();
        String latestSnapshotPath = "";
        for (CompactBenchmarkRegime regime : CompactBenchmarkRegime.values()) {
            for (long seed : seeds) {
                RunCaseResult baseline = runCase(seed, regime, SimulationEngine.DispatchMode.LEGACY, true);
                RunCaseResult compact = runCase(seed, regime, SimulationEngine.DispatchMode.COMPACT, false);
                RunCaseResult omega = runCase(seed, regime, SimulationEngine.DispatchMode.OMEGA, false);
                if (compact.weightSnapshot() != null) {
                    latestSnapshotPath = writeLatestSnapshot(lane, regime, seed, compact.weightSnapshot());
                }
                cases.add(new CompactBenchmarkCase(
                        seed,
                        regime.slug(),
                        baseline.report(),
                        compact.report(),
                        omega.report(),
                        compact.snapshotTag(),
                        compact.rollbackAvailable(),
                        compact.topExplanations(),
                        compact.planTypeCounts(),
                        compact.routeSourceCounts(),
                        compact.batchEligibleContexts(),
                        compact.batchChosenWhenEligibleContexts(),
                        compact.singleChosenWhenBatchEligibleContexts(),
                        compact.batchRejectionReasons(),
                        compact.calibrationSnapshot(),
                        compact.report().bundleSuccessRate(),
                        compact.report().avgObservedBundleSize(),
                        compact.report().bundleThreePlusRate()));
            }
        }

        double compactCompletionDelta = mean(cases, c -> c.compact().completionRate() - c.baseline().completionRate());
        double compactOnTimeDelta = mean(cases, c -> c.compact().onTimeRate() - c.baseline().onTimeRate());
        double compactDeadheadDelta = mean(cases, c -> c.compact().deadheadPerCompletedOrderKm() - c.baseline().deadheadPerCompletedOrderKm());
        double compactPostDropDelta = mean(cases, c -> c.compact().postDropOrderHitRate() - c.baseline().postDropOrderHitRate());
        double compactEmptyKmDelta = mean(cases, c -> c.compact().expectedPostCompletionEmptyKm() - c.baseline().expectedPostCompletionEmptyKm());
        double compactDeadheadImprovementPct = mean(cases, c -> relativeImprovement(
                c.baseline().deadheadPerCompletedOrderKm(),
                c.compact().deadheadPerCompletedOrderKm()));
        double compactEmptyKmImprovementPct = mean(cases, c -> relativeImprovement(
                c.baseline().expectedPostCompletionEmptyKm(),
                c.compact().expectedPostCompletionEmptyKm()));
        double compactCompletionVsOmega = mean(cases, c -> c.compact().completionRate() - c.omegaReference().completionRate());
        double compactDeadheadVsOmega = mean(cases, c -> c.compact().deadheadPerCompletedOrderKm() - c.omegaReference().deadheadPerCompletedOrderKm());
        Map<String, Integer> planTypeCounts = sumCounts(cases, CompactBenchmarkCase::compactPlanTypeCounts);
        Map<String, Double> selectedPlanTypeShare = shareCounts(planTypeCounts);
        Map<String, Integer> routeSourceCounts = sumCounts(cases, CompactBenchmarkCase::routeSourceCounts);
        int batchEligibleContexts = cases.stream().mapToInt(CompactBenchmarkCase::batchEligibleContexts).sum();
        int batchChosenWhenEligibleContexts = cases.stream().mapToInt(CompactBenchmarkCase::batchChosenWhenEligibleContexts).sum();
        int singleChosenWhenBatchEligibleContexts = cases.stream().mapToInt(CompactBenchmarkCase::singleChosenWhenBatchEligibleContexts).sum();
        double batchChosenWhenEligibleRate = batchEligibleContexts == 0
                ? 0.0
                : (batchChosenWhenEligibleContexts * 100.0) / batchEligibleContexts;
        Map<String, Integer> batchRejectionReasons = sumCounts(cases, CompactBenchmarkCase::batchRejectionReasons);
        CalibrationSnapshot calibrationSnapshot = aggregateCalibration(cases);
        double compactBundleSuccessRate = mean(cases, CompactBenchmarkCase::bundleSuccessRate);
        double compactAvgObservedBundleSize = mean(cases, CompactBenchmarkCase::avgObservedBundleSize);
        double compactBundleThreePlusRate = mean(cases, CompactBenchmarkCase::bundleThreePlusRate);
        boolean noSevereSeedRegression = cases.stream().allMatch(c ->
                c.compact().completionRate() >= c.baseline().completionRate() - 1.0
                        && c.compact().onTimeRate() >= c.baseline().onTimeRate() - 1.0);

        return new CompactBenchmarkSummary(
                Instant.now(),
                lane.taskName(),
                "HCMC + INSTANT",
                "NearestGreedyBaseline",
                List.of(CompactBenchmarkRegime.values()).stream().map(CompactBenchmarkRegime::slug).toList(),
                List.copyOf(cases),
                compactCompletionDelta,
                compactOnTimeDelta,
                compactDeadheadDelta,
                compactPostDropDelta,
                compactEmptyKmDelta,
                compactDeadheadImprovementPct,
                compactEmptyKmImprovementPct,
                planTypeCounts,
                selectedPlanTypeShare,
                routeSourceCounts,
                batchEligibleContexts,
                batchChosenWhenEligibleContexts,
                singleChosenWhenBatchEligibleContexts,
                batchChosenWhenEligibleRate,
                batchRejectionReasons,
                calibrationSnapshot,
                compactBundleSuccessRate,
                compactAvgObservedBundleSize,
                compactBundleThreePlusRate,
                noSevereSeedRegression,
                latestSnapshotPath,
                compactCompletionVsOmega,
                compactDeadheadVsOmega);
    }

    static RunCaseResult runCase(long seed,
                                 CompactBenchmarkRegime regime,
                                 SimulationEngine.DispatchMode mode,
                                 boolean nearestGreedyBaseline) {
        SimulationEngine engine = new SimulationEngine(seed);
        engine.setDispatchMode(mode);
        engine.setLegacyNearestGreedyMode(nearestGreedyBaseline);
        engine.setInitialDriverCount(regime.initialDriverCount());
        engine.setDemandMultiplier(regime.demandMultiplier());
        engine.setTrafficIntensity(regime.trafficIntensity());
        engine.setWeatherProfile(regime.weatherProfile());
        engine.setSimulationStartTime(regime.startHour(), 0);
        for (int i = 0; i < regime.ticks(); i++) {
            engine.tickHeadless();
        }
        RunReport report = engine.createRunReport(
                "compact-benchmark-" + regime.slug() + "-" + mode.name().toLowerCase(),
                seed);
        return new RunCaseResult(
                report,
                engine.getCurrentCompactWeightSnapshot(),
                engine.getCurrentCompactStatus().snapshotTag(),
                engine.getCurrentCompactStatus().rollbackAvailable(),
                planTypeCounts(engine),
                routeSourceCounts(engine),
                engine.getCompactBatchEligibleContextCount(),
                engine.getCompactBatchChosenWhenEligibleCount(),
                engine.getCompactSingleChosenWhenBatchEligibleCount(),
                engine.getCompactBatchRejectionReasons(),
                engine.getCurrentCompactStatus().calibrationSnapshot(),
                engine.getLatestCompactEvidence().explanations().stream()
                        .map(CompactDecisionExplanation::summary)
                        .limit(3)
                        .toList());
    }

    static void writeSummary(CompactBenchmarkSummary summary) {
        writeArtifacts(summary);
    }

    static void writeArtifacts(CompactBenchmarkSummary summary) {
        try {
            Files.createDirectories(OUTPUT_DIR);
            Files.writeString(
                    OUTPUT_DIR.resolve(summary.lane() + "-summary.json"),
                    GSON.toJson(summary),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(
                    OUTPUT_DIR.resolve(summary.lane() + "-summary.md"),
                    renderMarkdown(summary),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            writeCalibrationArtifacts(summary);
            writePerSeedMarkdown(summary);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write compact benchmark summary", e);
        }
    }

    static String renderMarkdown(CompactBenchmarkSummary summary) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Compact Benchmark Summary\n\n");
        builder.append("- Lane: ").append(summary.lane()).append('\n');
        builder.append("- Scope: ").append(summary.scope()).append('\n');
        builder.append("- Baseline: ").append(summary.baselinePolicy()).append('\n');
        builder.append("- Regimes: ").append(summary.regimes()).append('\n');
        builder.append("- Seeds: ").append(summary.cases().stream().map(c -> Long.toString(c.seed())).distinct().toList()).append('\n');
        builder.append("- Latest snapshot: ").append(summary.latestSnapshotPath().isBlank() ? "n/a" : summary.latestSnapshotPath()).append("\n\n");
        builder.append("## Aggregate\n");
        builder.append("- Completion vs baseline: ").append(format(summary.compactCompletionDeltaVsBaseline())).append(" pp\n");
        builder.append("- On-time vs baseline: ").append(format(summary.compactOnTimeDeltaVsBaseline())).append(" pp\n");
        builder.append("- Deadhead/order vs baseline: ").append(format(summary.compactDeadheadDeltaVsBaseline())).append('\n');
        builder.append("- Deadhead improvement vs baseline: ").append(format(summary.compactDeadheadImprovementPctVsBaseline())).append("%\n");
        builder.append("- Post-drop hit vs baseline: ").append(format(summary.compactPostDropHitDeltaVsBaseline())).append(" pp\n");
        builder.append("- Empty-km vs baseline: ").append(format(summary.compactEmptyKmDeltaVsBaseline())).append('\n');
        builder.append("- Empty-km improvement vs baseline: ").append(format(summary.compactEmptyKmImprovementPctVsBaseline())).append("%\n");
        builder.append("- Compact plan-type coverage: ").append(summary.compactPlanTypeCounts()).append('\n');
        builder.append("- Selected plan-type share: ").append(summary.selectedPlanTypeShare()).append('\n');
        builder.append("- Compact route-source coverage: ").append(summary.routeSourceCounts()).append('\n');
        builder.append("- Batch eligible contexts: ").append(summary.compactBatchEligibleContexts()).append('\n');
        builder.append("- Batch chosen when eligible: ").append(summary.compactBatchChosenWhenEligibleContexts()).append('\n');
        builder.append("- Single chosen when batch eligible: ").append(summary.compactSingleChosenWhenBatchEligibleContexts()).append('\n');
        builder.append("- Batch chosen when eligible rate: ").append(format(summary.compactBatchChosenWhenEligibleRate())).append("%\n");
        builder.append("- Batch rejection reasons: ").append(summary.compactBatchRejectionReasons()).append('\n');
        builder.append("- Calibration snapshot: ").append(summary.compactCalibrationSnapshot()).append('\n');
        builder.append("- Calibration support: ").append(calibrationSupportNote(summary.compactCalibrationSnapshot())).append('\n');
        builder.append("- Compact bundle success rate: ").append(format(summary.compactBundleSuccessRate())).append("%\n");
        builder.append("- Compact avg observed bundle size: ").append(format(summary.compactAvgObservedBundleSize())).append('\n');
        builder.append("- Compact 3+ bundle rate: ").append(format(summary.compactBundleThreePlusRate())).append("%\n");
        builder.append("- No severe seed regression: ").append(summary.noSevereSeedRegression()).append('\n');
        builder.append("- Completion vs omega: ").append(format(summary.compactCompletionDeltaVsOmega())).append('\n');
        builder.append("- Deadhead/order vs omega: ").append(format(summary.compactDeadheadDeltaVsOmega())).append("\n\n");
        builder.append("## Seeds\n");
        for (CompactBenchmarkCase benchmarkCase : summary.cases()) {
            builder.append("- ").append(benchmarkCase.regime())
                    .append(" / seed ").append(benchmarkCase.seed())
                    .append(": compact completion ")
                    .append(String.format("%.2f", benchmarkCase.compact().completionRate()))
                    .append("%, baseline ")
                    .append(String.format("%.2f", benchmarkCase.baseline().completionRate()))
                    .append("%, compact deadhead/order ")
                    .append(String.format("%.3f", benchmarkCase.compact().deadheadPerCompletedOrderKm()))
                    .append("km, snapshot ")
                    .append(benchmarkCase.compactSnapshotTag())
                    .append('\n');
            builder.append("  coverage: planTypes=").append(benchmarkCase.compactPlanTypeCounts())
                    .append(" routeSources=").append(benchmarkCase.routeSourceCounts())
                    .append(" batchEligible=").append(benchmarkCase.batchEligibleContexts())
                    .append(" batchChosen=").append(benchmarkCase.batchChosenWhenEligibleContexts())
                    .append(" avgBundle=").append(String.format("%.2f", benchmarkCase.avgObservedBundleSize()))
                    .append('\n');
            if (!benchmarkCase.batchRejectionReasons().isEmpty()) {
                builder.append("  rejections: ").append(benchmarkCase.batchRejectionReasons()).append('\n');
            }
            for (String explanation : benchmarkCase.compactTopExplanations()) {
                builder.append("  top: ").append(explanation).append('\n');
            }
        }
        return builder.toString();
    }

    private static void writePerSeedMarkdown(CompactBenchmarkSummary summary) throws IOException {
        Path perSeedDir = OUTPUT_DIR.resolve(summary.lane() + "-per-seed");
        Files.createDirectories(perSeedDir);
        for (CompactBenchmarkCase benchmarkCase : summary.cases()) {
            StringBuilder builder = new StringBuilder();
            builder.append("# Compact Seed Report\n\n");
            builder.append("- Lane: ").append(summary.lane()).append('\n');
            builder.append("- Regime: ").append(benchmarkCase.regime()).append('\n');
            builder.append("- Seed: ").append(benchmarkCase.seed()).append('\n');
            builder.append("- Snapshot: ").append(benchmarkCase.compactSnapshotTag()).append('\n');
            builder.append("- Rollback available: ").append(benchmarkCase.compactRollbackAvailable()).append("\n\n");
            builder.append("## Metrics\n");
            builder.append("- Completion compact/baseline: ")
                    .append(String.format("%.2f / %.2f", benchmarkCase.compact().completionRate(), benchmarkCase.baseline().completionRate()))
                    .append('\n');
            builder.append("- On-time compact/baseline: ")
                    .append(String.format("%.2f / %.2f", benchmarkCase.compact().onTimeRate(), benchmarkCase.baseline().onTimeRate()))
                    .append('\n');
            builder.append("- Deadhead per completed order compact/baseline: ")
                    .append(String.format("%.3f / %.3f", benchmarkCase.compact().deadheadPerCompletedOrderKm(), benchmarkCase.baseline().deadheadPerCompletedOrderKm()))
                    .append('\n');
            builder.append("- Post-drop hit compact/baseline: ")
                    .append(String.format("%.2f / %.2f", benchmarkCase.compact().postDropOrderHitRate(), benchmarkCase.baseline().postDropOrderHitRate()))
                    .append('\n');
            builder.append("- Empty-km compact/baseline: ")
                    .append(String.format("%.3f / %.3f", benchmarkCase.compact().expectedPostCompletionEmptyKm(), benchmarkCase.baseline().expectedPostCompletionEmptyKm()))
                    .append('\n');
            builder.append("- Compact plan types: ").append(benchmarkCase.compactPlanTypeCounts()).append('\n');
            builder.append("- Route sources: ").append(benchmarkCase.routeSourceCounts()).append('\n');
            builder.append("- Batch eligible contexts: ").append(benchmarkCase.batchEligibleContexts()).append('\n');
            builder.append("- Batch chosen when eligible: ").append(benchmarkCase.batchChosenWhenEligibleContexts()).append('\n');
            builder.append("- Single chosen when batch eligible: ").append(benchmarkCase.singleChosenWhenBatchEligibleContexts()).append('\n');
            builder.append("- Batch rejection reasons: ").append(benchmarkCase.batchRejectionReasons()).append('\n');
            builder.append("- Calibration snapshot: ").append(benchmarkCase.calibrationSnapshot()).append('\n');
            builder.append("- Bundle success rate: ").append(String.format("%.2f", benchmarkCase.bundleSuccessRate())).append("%\n");
            builder.append("- Avg observed bundle size: ").append(String.format("%.2f", benchmarkCase.avgObservedBundleSize())).append('\n');
            builder.append("- 3+ bundle rate: ").append(String.format("%.2f", benchmarkCase.bundleThreePlusRate())).append("%\n\n");
            builder.append("## Top Explanations\n");
            for (String explanation : benchmarkCase.compactTopExplanations()) {
                builder.append("- ").append(explanation).append('\n');
            }
            Files.writeString(
                    perSeedDir.resolve(benchmarkCase.regime() + "-seed-" + benchmarkCase.seed() + ".md"),
                    builder.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private static String writeLatestSnapshot(CompactBenchmarkLane lane,
                                              CompactBenchmarkRegime regime,
                                              long seed,
                                              WeightSnapshot snapshot) {
        try {
            Files.createDirectories(OUTPUT_DIR);
            Path path = OUTPUT_DIR.resolve(lane.taskName() + "-latest-weight-snapshot.json");
            Files.writeString(
                    path,
                    GSON.toJson(snapshot),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return OUTPUT_DIR.resolve(lane.taskName() + "-latest-weight-snapshot.json").toString()
                    + "#" + regime.slug() + "-seed-" + seed;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write compact snapshot artifact", e);
        }
    }

    private static double mean(List<CompactBenchmarkCase> cases, ToDoubleFunction<CompactBenchmarkCase> fn) {
        if (cases.isEmpty()) {
            return 0.0;
        }
        return cases.stream().mapToDouble(fn).average().orElse(0.0);
    }

    private static double relativeImprovement(double baseline, double challenger) {
        if (Math.abs(baseline) < 1e-9) {
            return 0.0;
        }
        return ((baseline - challenger) / Math.abs(baseline)) * 100.0;
    }

    private static String format(double value) {
        return String.format("%+.3f", value);
    }

    private static CalibrationSnapshot aggregateCalibration(List<CompactBenchmarkCase> cases) {
        if (cases.isEmpty()) {
            return CalibrationSnapshot.empty();
        }
        return new CalibrationSnapshot(
                mean(cases, c -> c.calibrationSnapshot().etaResidualMaeMinutes()),
                mean(cases, c -> c.calibrationSnapshot().cancelCalibrationGap()),
                mean(cases, c -> c.calibrationSnapshot().postDropHitCalibrationGap()),
                mean(cases, c -> c.calibrationSnapshot().nextIdleMaeMinutes()),
                mean(cases, c -> c.calibrationSnapshot().emptyKmMae()),
                cases.stream().mapToLong(c -> c.calibrationSnapshot().etaSamples()).sum(),
                cases.stream().mapToLong(c -> c.calibrationSnapshot().cancelSamples()).sum(),
                cases.stream().mapToLong(c -> c.calibrationSnapshot().postDropSamples()).sum());
    }

    private static void writeCalibrationArtifacts(CompactBenchmarkSummary summary) throws IOException {
        CalibrationSnapshot snapshot = summary.compactCalibrationSnapshot();
        String supportNote = calibrationSupportNote(snapshot);
        String baseName = summary.lane() + "-calibration";
        Files.writeString(
                OUTPUT_DIR.resolve(baseName + ".json"),
                GSON.toJson(snapshot),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(
                OUTPUT_DIR.resolve(baseName + ".md"),
                "# Compact Calibration Summary\n\n"
                        + "- Lane: " + summary.lane() + "\n"
                        + "- ETA residual MAE: " + format(snapshot.etaResidualMaeMinutes()) + "\n"
                        + "- Cancel calibration gap: " + format(snapshot.cancelCalibrationGap()) + "\n"
                        + "- Post-drop hit calibration gap: " + format(snapshot.postDropHitCalibrationGap()) + "\n"
                        + "- Next-idle MAE: " + format(snapshot.nextIdleMaeMinutes()) + "\n"
                        + "- Empty-km MAE: " + format(snapshot.emptyKmMae()) + "\n"
                        + "- ETA samples: " + snapshot.etaSamples() + "\n"
                        + "- Cancel samples: " + snapshot.cancelSamples() + "\n"
                        + "- Post-drop samples: " + snapshot.postDropSamples() + "\n"
                        + "- Support note: " + supportNote + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(
                OUTPUT_DIR.resolve(baseName + ".csv"),
                "lane,etaResidualMaeMinutes,cancelCalibrationGap,postDropHitCalibrationGap,nextIdleMaeMinutes,emptyKmMae,etaSamples,cancelSamples,postDropSamples,supportNote\n"
                        + "%s,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%s\n".formatted(
                        summary.lane(),
                        snapshot.etaResidualMaeMinutes(),
                        snapshot.cancelCalibrationGap(),
                        snapshot.postDropHitCalibrationGap(),
                        snapshot.nextIdleMaeMinutes(),
                        snapshot.emptyKmMae(),
                        snapshot.etaSamples(),
                        snapshot.cancelSamples(),
                        snapshot.postDropSamples(),
                        supportNote),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String calibrationSupportNote(CalibrationSnapshot snapshot) {
        if (snapshot == null || snapshot.etaSamples() < 10 || snapshot.cancelSamples() < 10 || snapshot.postDropSamples() < 10) {
            return "insufficient support";
        }
        return "observability only";
    }

    private static Map<String, Integer> planTypeCounts(SimulationEngine engine) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CompactPlanType type : CompactPlanType.values()) {
            counts.put(type.name(), engine.getCompactSelectedPlanTypeCounts().getOrDefault(type, 0));
        }
        return Map.copyOf(counts);
    }

    private static Map<String, Integer> routeSourceCounts(SimulationEngine engine) {
        int selectedPlans = engine.getCompactSelectedPlanTypeCounts().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("RUNTIME_OSRM", 0);
        counts.put("RUNTIME_FALLBACK", selectedPlans);
        return Map.copyOf(counts);
    }

    private static Map<String, Double> shareCounts(Map<String, Integer> counts) {
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Double> shares = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            double share = total == 0 ? 0.0 : (entry.getValue() * 100.0) / total;
            shares.put(entry.getKey(), share);
        }
        return Map.copyOf(shares);
    }

    private static Map<String, Integer> sumCounts(List<CompactBenchmarkCase> cases,
                                                  java.util.function.Function<CompactBenchmarkCase, Map<String, Integer>> extractor) {
        Map<String, Integer> total = new LinkedHashMap<>();
        for (CompactBenchmarkCase benchmarkCase : cases) {
            for (Map.Entry<String, Integer> entry : extractor.apply(benchmarkCase).entrySet()) {
                total.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return Map.copyOf(total);
    }

    record RunCaseResult(
            RunReport report,
            WeightSnapshot weightSnapshot,
            String snapshotTag,
            boolean rollbackAvailable,
            Map<String, Integer> planTypeCounts,
            Map<String, Integer> routeSourceCounts,
            int batchEligibleContexts,
            int batchChosenWhenEligibleContexts,
            int singleChosenWhenBatchEligibleContexts,
            Map<String, Integer> batchRejectionReasons,
            CalibrationSnapshot calibrationSnapshot,
            List<String> topExplanations) {
    }
}
