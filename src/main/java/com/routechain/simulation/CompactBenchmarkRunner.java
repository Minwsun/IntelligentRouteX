package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.core.CompactDecisionExplanation;
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
import java.util.List;
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
                        compact.topExplanations()));
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
                    .append("\n\n");
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

    record RunCaseResult(
            RunReport report,
            WeightSnapshot weightSnapshot,
            String snapshotTag,
            boolean rollbackAvailable,
            List<String> topExplanations) {
    }
}
