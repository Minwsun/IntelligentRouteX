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
import java.util.List;

public final class CompactVerdictRunner {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path OUTPUT_DIR = Path.of("build", "routechain-apex", "benchmarks", "compact");

    private CompactVerdictRunner() {
    }

    public static void main(String[] args) {
        CompactBenchmarkLane lane = CompactBenchmarkLane.fromArg(args.length == 0 ? null : args[0]);
        CompactBenchmarkSummary benchmarkSummary = CompactBenchmarkRunner.runBenchmark(lane);
        CompactBenchmarkRunner.writeArtifacts(benchmarkSummary);
        CompactVerdictSummary verdict = evaluate(benchmarkSummary);
        writeVerdict(verdict);
        System.out.println(renderMarkdown(verdict));
    }

    public static CompactVerdictSummary evaluate(CompactBenchmarkSummary summary) {
        List<String> notes = new ArrayList<>();
        boolean completionPass = summary.compactCompletionDeltaVsBaseline() >= -0.10;
        boolean onTimePass = summary.compactOnTimeDeltaVsBaseline() >= -0.20;
        boolean deadheadPass = summary.compactDeadheadImprovementPctVsBaseline() >= 1.0;
        boolean postDropHitPass = summary.compactPostDropHitDeltaVsBaseline() >= 2.0;
        boolean emptyKmPass = summary.compactEmptyKmImprovementPctVsBaseline() >= 3.0;
        boolean noSevereRegressionPass = summary.noSevereSeedRegression();

        if (!completionPass) {
            notes.add("completion is below baseline tolerance");
        }
        if (!onTimePass) {
            notes.add("on-time is below baseline tolerance");
        }
        if (!deadheadPass) {
            notes.add("deadhead per completed order is not yet better than baseline");
        }
        if (!postDropHitPass) {
            notes.add("post-drop hit is not yet better than baseline");
        }
        if (!emptyKmPass) {
            notes.add("post-completion empty km is not yet better than baseline");
        }
        if (!noSevereRegressionPass) {
            notes.add("at least one seed has a material regression against baseline");
        }
        if (summary.compactBatchEligibleContexts() == 0) {
            notes.add("no batch-eligible contexts were observed in this lane");
        } else {
            notes.add("batch chosen when eligible rate="
                    + String.format("%+.3f%%", summary.compactBatchChosenWhenEligibleRate()));
            if (!summary.compactBatchRejectionReasons().isEmpty()) {
                notes.add("batch rejection reasons=" + summary.compactBatchRejectionReasons());
            }
        }
        if (summary.compactCalibrationSnapshot().etaSamples() < 10
                || summary.compactCalibrationSnapshot().cancelSamples() < 10
                || summary.compactCalibrationSnapshot().postDropSamples() < 10) {
            notes.add("calibration support is still low; metrics remain observability-only");
        } else {
            notes.add("calibration health etaMae="
                    + String.format("%.3f", summary.compactCalibrationSnapshot().etaResidualMaeMinutes())
                    + " cancelGap=" + String.format("%.3f", summary.compactCalibrationSnapshot().cancelCalibrationGap())
                    + " postDropGap=" + String.format("%.3f", summary.compactCalibrationSnapshot().postDropHitCalibrationGap()));
        }

        boolean overallPass = completionPass
                && onTimePass
                && deadheadPass
                && postDropHitPass
                && emptyKmPass
                && noSevereRegressionPass;

        if (overallPass) {
            notes.add("compact lane is eligible for controlled cutover review");
        }

        return new CompactVerdictSummary(
                Instant.now(),
                summary.lane(),
                summary.scope(),
                completionPass,
                onTimePass,
                deadheadPass,
                postDropHitPass,
                emptyKmPass,
                noSevereRegressionPass,
                overallPass,
                summary.compactCompletionDeltaVsBaseline(),
                summary.compactOnTimeDeltaVsBaseline(),
                summary.compactDeadheadImprovementPctVsBaseline(),
                summary.compactPostDropHitDeltaVsBaseline(),
                summary.compactEmptyKmImprovementPctVsBaseline(),
                summary.compactBatchChosenWhenEligibleRate(),
                summary.compactCalibrationSnapshot(),
                List.copyOf(notes),
                summary);
    }

    static void writeVerdict(CompactVerdictSummary verdict) {
        try {
            Files.createDirectories(OUTPUT_DIR);
            Files.writeString(
                    OUTPUT_DIR.resolve(verdict.lane() + "-verdict.json"),
                    GSON.toJson(verdict),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(
                    OUTPUT_DIR.resolve(verdict.lane() + "-verdict.md"),
                    renderMarkdown(verdict),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write compact verdict", e);
        }
    }

    static String renderMarkdown(CompactVerdictSummary verdict) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Compact Verdict\n\n");
        builder.append("- Lane: ").append(verdict.lane()).append('\n');
        builder.append("- Scope: ").append(verdict.scope()).append('\n');
        builder.append("- Overall pass: ").append(verdict.overallPass()).append('\n');
        builder.append("- Completion pass: ").append(verdict.completionPass()).append('\n');
        builder.append("- On-time pass: ").append(verdict.onTimePass()).append('\n');
        builder.append("- Deadhead pass: ").append(verdict.deadheadPass()).append('\n');
        builder.append("- Post-drop hit pass: ").append(verdict.postDropHitPass()).append('\n');
        builder.append("- Empty-km pass: ").append(verdict.emptyKmPass()).append('\n');
        builder.append("- No severe regression pass: ").append(verdict.noSevereRegressionPass()).append('\n');
        builder.append("- Completion delta vs baseline: ").append(String.format("%+.3f pp", verdict.completionDeltaVsBaseline())).append('\n');
        builder.append("- On-time delta vs baseline: ").append(String.format("%+.3f pp", verdict.onTimeDeltaVsBaseline())).append('\n');
        builder.append("- Deadhead improvement vs baseline: ").append(String.format("%+.3f%%", verdict.deadheadImprovementPctVsBaseline())).append('\n');
        builder.append("- Post-drop hit delta vs baseline: ").append(String.format("%+.3f pp", verdict.postDropHitDeltaVsBaseline())).append('\n');
        builder.append("- Empty-km improvement vs baseline: ").append(String.format("%+.3f%%", verdict.emptyKmImprovementPctVsBaseline())).append("\n\n");
        builder.append("- Batch chosen when eligible rate: ").append(String.format("%+.3f%%", verdict.batchChosenWhenEligibleRate())).append("\n\n");
        builder.append("- Calibration snapshot: ").append(verdict.compactCalibrationSnapshot()).append("\n\n");
        builder.append("## Notes\n");
        for (String note : verdict.notes()) {
            builder.append("- ").append(note).append('\n');
        }
        return builder.toString();
    }
}
