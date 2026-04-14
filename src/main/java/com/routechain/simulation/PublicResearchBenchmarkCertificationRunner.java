package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.infra.GsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Summarizes public benchmark evidence from Track B research families.
 */
public final class PublicResearchBenchmarkCertificationRunner {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path ROOT = Path.of("build", "routechain-apex", "benchmarks");
    private static final Path RUNS_DIR = ROOT.resolve("runs");
    private static final Path COMPARES_DIR = ROOT.resolve("compares");
    private static final Path DATASET_ROOT = Path.of("benchmarks", "vrp");
    private static final List<String> REQUIRED_FAMILIES = List.of(
            "solomon",
            "homberger",
            "li-lim-pdptw"
    );

    private PublicResearchBenchmarkCertificationRunner() {}

    public static void main(String[] args) {
        String laneName = args.length > 0 && !args[0].isBlank()
                ? args[0].trim().toLowerCase(Locale.ROOT)
                : "certification";
        List<RunReport> runs = readJsonDirectory(RUNS_DIR, RunReport.class);
        List<ReplayCompareResult> compares = readJsonDirectory(COMPARES_DIR, ReplayCompareResult.class);

        List<ResearchBenchmarkFamilyResult> families = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (String family : REQUIRED_FAMILIES) {
            families.add(evaluateFamily(family, runs, compares));
        }
        boolean overallPass = families.stream().allMatch(ResearchBenchmarkFamilyResult::pass);
        if (!overallPass) {
            notes.add("at least one public research family is missing or regressed");
        }

        PublicResearchBenchmarkSummary summary = new PublicResearchBenchmarkSummary(
                BenchmarkSchema.VERSION,
                "public-research-benchmark-" + laneName,
                Instant.now(),
                BenchmarkCertificationSupport.resolveGitRevision(),
                families,
                overallPass,
                notes
        );
        BenchmarkArtifactWriter.writePublicResearchBenchmarkSummary(summary);
        System.out.println("[PublicResearchBenchmark] lane=" + laneName
                + " overallPass=" + summary.overallPass()
                + " families=" + summary.familyResults().size());
        if (!summary.overallPass()) {
            throw new IllegalStateException("Public research benchmark certification failed for lane " + laneName);
        }
    }

    private static ResearchBenchmarkFamilyResult evaluateFamily(String family,
                                                               List<RunReport> runs,
                                                               List<ReplayCompareResult> compares) {
        String marker = ("b-" + family + "-").toLowerCase(Locale.ROOT);
        List<ReplayCompareResult> familyCompares = compares.stream()
                .filter(BenchmarkCertificationSupport::isCurrentOmegaCompare)
                .filter(compare -> BenchmarkCertificationSupport.normalize(compare.runIdB()).contains(marker))
                .toList();
        List<RunReport> omegaRuns = runs.stream()
                .filter(BenchmarkCertificationSupport::isCurrentOmegaRun)
                .filter(run -> BenchmarkCertificationSupport.normalize(run.runId()).contains(marker))
                .toList();
        List<String> notes = new ArrayList<>();
        if (datasetFamilyIsEmpty(family)) {
            notes.add("dataset directory is empty for family " + family
                    + "; run scripts/benchmark/fetch_route_research_datasets.ps1 before certification");
        }
        if (familyCompares.isEmpty()) {
            notes.add("missing compare artifacts for family " + family);
            return new ResearchBenchmarkFamilyResult(family, 0, 0.0, 0.0, 0.0, 0.0, 0.0, false, notes);
        }
        double gainMean = BenchmarkCertificationSupport.average(
                familyCompares.stream().map(ReplayCompareResult::overallGainPercent).toList());
        double completionMean = BenchmarkCertificationSupport.average(
                familyCompares.stream().map(ReplayCompareResult::completionRateDelta).toList());
        double deadheadMean = BenchmarkCertificationSupport.average(
                familyCompares.stream().map(ReplayCompareResult::deadheadRatioDelta).toList());
        double postDropMean = BenchmarkCertificationSupport.average(
                familyCompares.stream().map(ReplayCompareResult::postDropOrderHitRateDelta).toList());
        double safetyPassRate = omegaRuns.isEmpty()
                ? 0.0
                : BenchmarkCertificationSupport.average(omegaRuns.stream()
                .map(run -> run.acceptance().safetyPass() ? 100.0 : 0.0)
                .toList());
        boolean pass = !omegaRuns.isEmpty()
                && gainMean >= -0.5
                && completionMean >= -0.25
                && deadheadMean <= 0.5
                && safetyPassRate >= 100.0;
        if (omegaRuns.isEmpty()) {
            notes.add("missing omega run artifacts for family " + family);
        }
        if (gainMean < -0.5) {
            notes.add("overall gain regressed below tolerance");
        }
        if (completionMean < -0.25) {
            notes.add("completion delta regressed below tolerance");
        }
        if (deadheadMean > 0.5) {
            notes.add("deadhead delta regressed above tolerance");
        }
        if (safetyPassRate < 100.0) {
            notes.add("at least one research run missed safety acceptance");
        }
        return new ResearchBenchmarkFamilyResult(
                family,
                familyCompares.size(),
                gainMean,
                completionMean,
                deadheadMean,
                postDropMean,
                safetyPassRate,
                pass,
                notes
        );
    }

    private static boolean datasetFamilyIsEmpty(String family) {
        Path familyDir = DATASET_ROOT.resolve(family);
        try {
            if (Files.notExists(familyDir) || !Files.isDirectory(familyDir)) {
                return true;
            }
            try (var stream = Files.walk(familyDir)) {
                return stream.noneMatch(Files::isRegularFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to inspect public benchmark dataset directory " + familyDir, e);
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
            throw new IllegalStateException("Unable to read benchmark artifact directory " + dir, e);
        }
    }
}
