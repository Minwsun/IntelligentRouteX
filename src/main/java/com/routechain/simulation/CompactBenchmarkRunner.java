package com.routechain.simulation;

import com.google.gson.Gson;
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

public final class CompactBenchmarkRunner {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path OUTPUT_DIR = Path.of("build", "routechain-apex", "benchmarks", "compact");
    private static final List<Long> DEFAULT_SEEDS = List.of(2026041101L, 2026041102L, 2026041103L);

    private CompactBenchmarkRunner() {
    }

    public static void main(String[] args) {
        CompactBenchmarkSummary summary = runBenchmark(DEFAULT_SEEDS);
        writeSummary(summary);
        System.out.println(renderMarkdown(summary));
    }

    public static CompactBenchmarkSummary runBenchmark(List<Long> seeds) {
        List<CompactBenchmarkCase> cases = new ArrayList<>();
        for (long seed : seeds) {
            RunReport baseline = runCase(seed, SimulationEngine.DispatchMode.LEGACY, true);
            RunReport compact = runCase(seed, SimulationEngine.DispatchMode.COMPACT, false);
            RunReport omega = runCase(seed, SimulationEngine.DispatchMode.OMEGA, false);
            cases.add(new CompactBenchmarkCase(seed, baseline, compact, omega));
        }

        double compactCompletionDelta = mean(cases, c -> c.compact().completionRate() - c.baseline().completionRate());
        double compactOnTimeDelta = mean(cases, c -> c.compact().onTimeRate() - c.baseline().onTimeRate());
        double compactDeadheadDelta = mean(cases, c -> c.compact().deadheadPerCompletedOrderKm() - c.baseline().deadheadPerCompletedOrderKm());
        double compactPostDropDelta = mean(cases, c -> c.compact().postDropOrderHitRate() - c.baseline().postDropOrderHitRate());
        double compactEmptyKmDelta = mean(cases, c -> c.compact().expectedPostCompletionEmptyKm() - c.baseline().expectedPostCompletionEmptyKm());
        double compactCompletionVsOmega = mean(cases, c -> c.compact().completionRate() - c.omegaReference().completionRate());
        double compactDeadheadVsOmega = mean(cases, c -> c.compact().deadheadPerCompletedOrderKm() - c.omegaReference().deadheadPerCompletedOrderKm());

        return new CompactBenchmarkSummary(
                Instant.now(),
                "HCMC + INSTANT",
                "NearestGreedyBaseline",
                List.copyOf(cases),
                compactCompletionDelta,
                compactOnTimeDelta,
                compactDeadheadDelta,
                compactPostDropDelta,
                compactEmptyKmDelta,
                compactCompletionVsOmega,
                compactDeadheadVsOmega);
    }

    static RunReport runCase(long seed, SimulationEngine.DispatchMode mode, boolean nearestGreedyBaseline) {
        SimulationEngine engine = new SimulationEngine(seed);
        engine.setDispatchMode(mode);
        engine.setLegacyNearestGreedyMode(nearestGreedyBaseline);
        engine.setInitialDriverCount(8);
        engine.setDemandMultiplier(0.14);
        engine.setTrafficIntensity(0.28);
        engine.setWeatherProfile(WeatherProfile.CLEAR);
        engine.setSimulationStartTime(12, 0);
        for (int i = 0; i < 360; i++) {
            engine.tickHeadless();
        }
        return engine.createRunReport("compact-benchmark-" + mode.name().toLowerCase(), seed);
    }

    static void writeSummary(CompactBenchmarkSummary summary) {
        try {
            Files.createDirectories(OUTPUT_DIR);
            Files.writeString(
                    OUTPUT_DIR.resolve("compact-benchmark-summary.json"),
                    GSON.toJson(summary),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(
                    OUTPUT_DIR.resolve("compact-benchmark-summary.md"),
                    renderMarkdown(summary),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write compact benchmark summary", e);
        }
    }

    static String renderMarkdown(CompactBenchmarkSummary summary) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Compact Benchmark Summary\n\n");
        builder.append("- Scope: ").append(summary.scope()).append('\n');
        builder.append("- Baseline: ").append(summary.baselinePolicy()).append('\n');
        builder.append("- Seeds: ").append(summary.cases().stream().map(c -> Long.toString(c.seed())).toList()).append("\n\n");
        builder.append("## Aggregate\n");
        builder.append("- Completion vs baseline: ").append(format(summary.compactCompletionDeltaVsBaseline())).append('\n');
        builder.append("- On-time vs baseline: ").append(format(summary.compactOnTimeDeltaVsBaseline())).append('\n');
        builder.append("- Deadhead/order vs baseline: ").append(format(summary.compactDeadheadDeltaVsBaseline())).append('\n');
        builder.append("- Post-drop hit vs baseline: ").append(format(summary.compactPostDropHitDeltaVsBaseline())).append('\n');
        builder.append("- Empty-km vs baseline: ").append(format(summary.compactEmptyKmDeltaVsBaseline())).append('\n');
        builder.append("- Completion vs omega: ").append(format(summary.compactCompletionDeltaVsOmega())).append('\n');
        builder.append("- Deadhead/order vs omega: ").append(format(summary.compactDeadheadDeltaVsOmega())).append("\n\n");
        builder.append("## Seeds\n");
        for (CompactBenchmarkCase benchmarkCase : summary.cases()) {
            builder.append("- Seed ").append(benchmarkCase.seed())
                    .append(": compact completion ")
                    .append(String.format("%.2f", benchmarkCase.compact().completionRate()))
                    .append("%, baseline ")
                    .append(String.format("%.2f", benchmarkCase.baseline().completionRate()))
                    .append("%, compact deadhead/order ")
                    .append(String.format("%.3f", benchmarkCase.compact().deadheadPerCompletedOrderKm()))
                    .append("km\n");
        }
        return builder.toString();
    }

    private static double mean(List<CompactBenchmarkCase> cases, java.util.function.ToDoubleFunction<CompactBenchmarkCase> fn) {
        if (cases.isEmpty()) {
            return 0.0;
        }
        return cases.stream().mapToDouble(fn).average().orElse(0.0);
    }

    private static String format(double value) {
        return String.format("%+.3f", value);
    }
}
