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
        CompactBenchmarkSummary benchmarkSummary = CompactBenchmarkRunner.runBenchmark(
                List.of(2026041101L, 2026041102L, 2026041103L));
        CompactBenchmarkRunner.writeSummary(benchmarkSummary);
        CompactVerdictSummary verdict = evaluate(benchmarkSummary);
        writeVerdict(verdict);
        System.out.println(renderMarkdown(verdict));
    }

    public static CompactVerdictSummary evaluate(CompactBenchmarkSummary summary) {
        List<String> notes = new ArrayList<>();
        boolean completionPass = summary.compactCompletionDeltaVsBaseline() >= -0.10;
        boolean onTimePass = summary.compactOnTimeDeltaVsBaseline() >= -0.10;
        boolean deadheadPass = summary.compactDeadheadDeltaVsBaseline() < 0.0;
        boolean postDropHitPass = summary.compactPostDropHitDeltaVsBaseline() > 0.0;
        boolean emptyKmPass = summary.compactEmptyKmDeltaVsBaseline() < 0.0;
        boolean noSevereRegressionPass = summary.cases().stream().allMatch(c ->
                c.compact().completionRate() >= c.baseline().completionRate() - 1.0
                        && c.compact().onTimeRate() >= c.baseline().onTimeRate() - 1.0);

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
                summary.scope(),
                completionPass,
                onTimePass,
                deadheadPass,
                postDropHitPass,
                emptyKmPass,
                noSevereRegressionPass,
                overallPass,
                List.copyOf(notes),
                summary);
    }

    static void writeVerdict(CompactVerdictSummary verdict) {
        try {
            Files.createDirectories(OUTPUT_DIR);
            Files.writeString(
                    OUTPUT_DIR.resolve("compact-verdict.json"),
                    GSON.toJson(verdict),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(
                    OUTPUT_DIR.resolve("compact-verdict.md"),
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
        builder.append("- Scope: ").append(verdict.scope()).append('\n');
        builder.append("- Overall pass: ").append(verdict.overallPass()).append('\n');
        builder.append("- Completion pass: ").append(verdict.completionPass()).append('\n');
        builder.append("- On-time pass: ").append(verdict.onTimePass()).append('\n');
        builder.append("- Deadhead pass: ").append(verdict.deadheadPass()).append('\n');
        builder.append("- Post-drop hit pass: ").append(verdict.postDropHitPass()).append('\n');
        builder.append("- Empty-km pass: ").append(verdict.emptyKmPass()).append('\n');
        builder.append("- No severe regression pass: ").append(verdict.noSevereRegressionPass()).append("\n\n");
        builder.append("## Notes\n");
        for (String note : verdict.notes()) {
            builder.append("- ").append(note).append('\n');
        }
        return builder.toString();
    }
}
