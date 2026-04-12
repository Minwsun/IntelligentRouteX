package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.infra.GsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CompactCertificationRunner {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path OUTPUT_DIR = Path.of("build", "routechain-apex", "benchmarks", "compact");
    private static final double COMPLETION_DELTA_TOLERANCE_PP = -0.25;
    private static final double ON_TIME_DELTA_TOLERANCE_PP = -0.50;
    private static final double DEADHEAD_DELTA_TOLERANCE_KM = 0.10;
    private static final double EMPTY_KM_DELTA_TOLERANCE_KM = 0.15;
    private static final double POST_DROP_DELTA_TOLERANCE_PP = -2.00;

    private CompactCertificationRunner() {
    }

    public static void main(String[] args) {
        CompactBenchmarkLane lane = CompactBenchmarkLane.fromArg(args.length == 0 ? null : args[0]);
        CompactBenchmarkSummary benchmarkSummary = CompactBenchmarkRunner.runBenchmark(lane);
        CompactBenchmarkRunner.writeArtifacts(benchmarkSummary);
        CompactCertificationSummary certification = evaluate(benchmarkSummary);
        writeArtifacts(certification);
        System.out.println(renderMarkdown(certification));
        if (!certification.overallPass()) {
            throw new IllegalStateException("Compact certification failed for lane " + lane.taskName());
        }
    }

    static CompactCertificationSummary evaluate(CompactBenchmarkSummary summary) {
        List<CompactCertificationRegimeResult> regimeResults = summary.cases().stream()
                .map(CompactBenchmarkCase::regime)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .map(regime -> evaluateRegime(summary, regime))
                .toList();

        double completionDeltaVsOmega = mean(summary.cases(),
                c -> c.compact().completionRate() - c.omegaReference().completionRate());
        double onTimeDeltaVsOmega = mean(summary.cases(),
                c -> c.compact().onTimeRate() - c.omegaReference().onTimeRate());
        double deadheadDeltaVsOmega = mean(summary.cases(),
                c -> c.compact().deadheadPerCompletedOrderKm() - c.omegaReference().deadheadPerCompletedOrderKm());
        double emptyKmDeltaVsOmega = mean(summary.cases(),
                c -> c.compact().expectedPostCompletionEmptyKm() - c.omegaReference().expectedPostCompletionEmptyKm());
        double postDropHitDeltaVsOmega = mean(summary.cases(),
                c -> c.compact().postDropOrderHitRate() - c.omegaReference().postDropOrderHitRate());

        boolean completionPass = completionDeltaVsOmega >= COMPLETION_DELTA_TOLERANCE_PP;
        boolean onTimePass = onTimeDeltaVsOmega >= ON_TIME_DELTA_TOLERANCE_PP;
        boolean deadheadPass = deadheadDeltaVsOmega <= DEADHEAD_DELTA_TOLERANCE_KM;
        boolean emptyKmPass = emptyKmDeltaVsOmega <= EMPTY_KM_DELTA_TOLERANCE_KM;
        boolean postDropHitPass = postDropHitDeltaVsOmega >= POST_DROP_DELTA_TOLERANCE_PP;
        boolean regimePass = regimeResults.stream().allMatch(CompactCertificationRegimeResult::pass);

        List<String> notes = new ArrayList<>();
        if (!completionPass) {
            notes.add("completion falls behind OMEGA beyond tolerance");
        }
        if (!onTimePass) {
            notes.add("on-time falls behind OMEGA beyond tolerance");
        }
        if (!deadheadPass) {
            notes.add("deadhead per completed order is materially worse than OMEGA");
        }
        if (!emptyKmPass) {
            notes.add("post-completion empty km is materially worse than OMEGA");
        }
        if (!postDropHitPass) {
            notes.add("post-drop hit falls behind OMEGA beyond tolerance");
        }
        if (!regimePass) {
            notes.add("at least one compact regime fails the graduation gate");
        }
        if (summary.compactCalibrationSnapshot().etaSamples() < 10
                || summary.compactCalibrationSnapshot().cancelSamples() < 10
                || summary.compactCalibrationSnapshot().postDropSamples() < 10) {
            notes.add("calibration health is observability-only because support is still low");
        } else {
            notes.add("calibration health is reported for observability only and does not change the gate");
        }
        notes.add("AFTER_ACCEPT currently means assignment activated on runtime driver sequence, not marketplace offer acceptance");
        notes.add("OMEGA remains the default reference until verdict=COMPACT_READY");

        boolean overallPass = completionPass
                && onTimePass
                && deadheadPass
                && emptyKmPass
                && postDropHitPass
                && regimePass;

        return new CompactCertificationSummary(
                Instant.now(),
                summary.lane(),
                summary.scope(),
                overallPass ? "COMPACT_READY" : "COMPACT_NOT_READY",
                completionPass,
                onTimePass,
                deadheadPass,
                emptyKmPass,
                postDropHitPass,
                overallPass,
                completionDeltaVsOmega,
                onTimeDeltaVsOmega,
                deadheadDeltaVsOmega,
                emptyKmDeltaVsOmega,
                postDropHitDeltaVsOmega,
                summary.compactCalibrationSnapshot(),
                regimeResults,
                List.copyOf(notes),
                summary);
    }

    private static CompactCertificationRegimeResult evaluateRegime(CompactBenchmarkSummary summary, String regime) {
        List<CompactBenchmarkCase> cases = summary.cases().stream()
                .filter(benchmarkCase -> benchmarkCase.regime().equals(regime))
                .toList();
        double completionDelta = mean(cases,
                c -> c.compact().completionRate() - c.omegaReference().completionRate());
        double onTimeDelta = mean(cases,
                c -> c.compact().onTimeRate() - c.omegaReference().onTimeRate());
        double deadheadDelta = mean(cases,
                c -> c.compact().deadheadPerCompletedOrderKm() - c.omegaReference().deadheadPerCompletedOrderKm());
        double emptyKmDelta = mean(cases,
                c -> c.compact().expectedPostCompletionEmptyKm() - c.omegaReference().expectedPostCompletionEmptyKm());
        double postDropDelta = mean(cases,
                c -> c.compact().postDropOrderHitRate() - c.omegaReference().postDropOrderHitRate());

        List<String> notes = new ArrayList<>();
        boolean pass = true;
        if (completionDelta < COMPLETION_DELTA_TOLERANCE_PP) {
            pass = false;
            notes.add("completion delta=" + format(completionDelta) + "pp");
        }
        if (onTimeDelta < ON_TIME_DELTA_TOLERANCE_PP) {
            pass = false;
            notes.add("on-time delta=" + format(onTimeDelta) + "pp");
        }
        if (deadheadDelta > DEADHEAD_DELTA_TOLERANCE_KM) {
            pass = false;
            notes.add("deadhead delta=" + format(deadheadDelta) + "km/order");
        }
        if (emptyKmDelta > EMPTY_KM_DELTA_TOLERANCE_KM) {
            pass = false;
            notes.add("empty-km delta=" + format(emptyKmDelta) + "km");
        }
        if (postDropDelta < POST_DROP_DELTA_TOLERANCE_PP) {
            pass = false;
            notes.add("post-drop delta=" + format(postDropDelta) + "pp");
        }
        if (notes.isEmpty()) {
            notes.add("within graduation tolerances");
        }

        return new CompactCertificationRegimeResult(
                regime,
                cases.size(),
                completionDelta,
                onTimeDelta,
                deadheadDelta,
                emptyKmDelta,
                postDropDelta,
                pass,
                List.copyOf(notes));
    }

    static void writeArtifacts(CompactCertificationSummary certification) {
        try {
            Files.createDirectories(OUTPUT_DIR);
            String baseName = certification.lane() + "-certification";
            Files.writeString(
                    OUTPUT_DIR.resolve(baseName + ".json"),
                    GSON.toJson(certification),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(
                    OUTPUT_DIR.resolve(baseName + ".md"),
                    renderMarkdown(certification),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write compact certification summary", e);
        }
    }

    static String renderMarkdown(CompactCertificationSummary certification) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Compact Certification\n\n");
        builder.append("- Lane: ").append(certification.lane()).append('\n');
        builder.append("- Scope: ").append(certification.scope()).append('\n');
        builder.append("- Verdict: ").append(certification.verdict()).append('\n');
        builder.append("- Overall pass: ").append(certification.overallPass()).append('\n');
        builder.append("- Completion pass: ").append(certification.completionPass()).append('\n');
        builder.append("- On-time pass: ").append(certification.onTimePass()).append('\n');
        builder.append("- Deadhead pass: ").append(certification.deadheadPass()).append('\n');
        builder.append("- Empty-km pass: ").append(certification.emptyKmPass()).append('\n');
        builder.append("- Post-drop hit pass: ").append(certification.postDropHitPass()).append('\n');
        builder.append("- Completion delta vs OMEGA: ").append(format(certification.completionDeltaVsOmega())).append(" pp\n");
        builder.append("- On-time delta vs OMEGA: ").append(format(certification.onTimeDeltaVsOmega())).append(" pp\n");
        builder.append("- Deadhead/order delta vs OMEGA: ").append(format(certification.deadheadDeltaVsOmega())).append(" km\n");
        builder.append("- Empty-km delta vs OMEGA: ").append(format(certification.emptyKmDeltaVsOmega())).append(" km\n");
        builder.append("- Post-drop hit delta vs OMEGA: ").append(format(certification.postDropHitDeltaVsOmega())).append(" pp\n");
        builder.append("- Calibration snapshot: ").append(certification.compactCalibrationSnapshot()).append("\n\n");
        builder.append("## Regimes\n");
        for (CompactCertificationRegimeResult regimeResult : certification.regimeResults()) {
            builder.append("- ").append(regimeResult.regime())
                    .append(": pass=").append(regimeResult.pass())
                    .append(" samples=").append(regimeResult.sampleCount())
                    .append(" completion=").append(format(regimeResult.completionDeltaVsOmega()))
                    .append("pp onTime=").append(format(regimeResult.onTimeDeltaVsOmega()))
                    .append("pp deadhead=").append(format(regimeResult.deadheadDeltaVsOmega()))
                    .append("km empty=").append(format(regimeResult.emptyKmDeltaVsOmega()))
                    .append("km postDrop=").append(format(regimeResult.postDropHitDeltaVsOmega()))
                    .append("pp\n");
            for (String note : regimeResult.notes()) {
                builder.append("  ").append(note).append('\n');
            }
        }
        builder.append("\n## Notes\n");
        for (String note : certification.notes()) {
            builder.append("- ").append(note).append('\n');
        }
        return builder.toString();
    }

    private static double mean(List<CompactBenchmarkCase> cases,
                               java.util.function.ToDoubleFunction<CompactBenchmarkCase> extractor) {
        if (cases.isEmpty()) {
            return 0.0;
        }
        return cases.stream().mapToDouble(extractor).average().orElse(0.0);
    }

    private static String format(double value) {
        return String.format("%+.3f", value);
    }
}
