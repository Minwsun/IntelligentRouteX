package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.infra.GsonSupport;
import com.routechain.infra.PlatformRuntimeBootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Writes benchmark artifacts to durable local storage for defense/demo use.
 */
public final class BenchmarkArtifactWriter {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path ROOT = Path.of("build", "routechain-apex", "benchmarks");
    private static final Path RUNS_DIR = ROOT.resolve("runs");
    private static final Path COMPARES_DIR = ROOT.resolve("compares");
    private static final Path RUNS_CSV = ROOT.resolve("run_reports.csv");
    private static final Path COMPARES_CSV = ROOT.resolve("replay_compare_results.csv");

    private BenchmarkArtifactWriter() {}

    public static void writeRun(RunReport report) {
        try {
            Files.createDirectories(RUNS_DIR);
            Files.writeString(
                    RUNS_DIR.resolve(report.runId() + ".json"),
                    GSON.toJson(report),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            appendCsv(RUNS_CSV,
                    "runId,scenario,seed,completion,onTime,cancel,deadhead,visible3plus,corridor,goodLast,emptyKm,nextIdle,realAssign,steadyAssign,launch3,recover3,downgrade,augment,holdOnly",
                    String.format(
                            "%s,%s,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f",
                            safe(report.runId()),
                            safe(report.scenarioName()),
                            report.seed(),
                            report.completionRate(),
                            report.onTimeRate(),
                            report.cancellationRate(),
                            report.deadheadDistanceRatio(),
                            report.visibleBundleThreePlusRate(),
                            report.deliveryCorridorQuality(),
                            report.lastDropGoodZoneRate(),
                            report.expectedPostCompletionEmptyKm(),
                            report.nextOrderIdleMinutes(),
                            report.realAssignmentRate(),
                            report.nonDowngradedRealAssignmentRate(),
                            report.thirdOrderLaunchRate(),
                            report.cleanWaveRecoveryRate(),
                            report.stressDowngradeRate(),
                            report.prePickupAugmentRate(),
                            report.holdOnlySelectionRate()));
            PlatformRuntimeBootstrap.recordRunReport(report);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write benchmark run artifact", e);
        }
    }

    public static void writeCompare(ReplayCompareResult compare) {
        try {
            Files.createDirectories(COMPARES_DIR);
            String fileName = safe(compare.runIdA()) + "__vs__" + safe(compare.runIdB()) + ".json";
            Files.writeString(
                    COMPARES_DIR.resolve(fileName),
                    GSON.toJson(compare),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            appendCsv(COMPARES_CSV,
                    "runIdA,runIdB,scenarioA,scenarioB,verdict,gain,completionDelta,onTimeDelta,cancelDelta,deadheadDelta,visible3plusDelta,corridorDelta,goodLastDelta,emptyKmDelta,realAssignDelta,steadyAssignDelta,wait3Delta,launch3Delta,recover3Delta,downgradeDelta,augmentDelta,holdOnlyDelta",
                    String.format(
                            "%s,%s,%s,%s,%s,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f",
                            safe(compare.runIdA()),
                            safe(compare.runIdB()),
                            safe(compare.scenarioA()),
                            safe(compare.scenarioB()),
                            safe(compare.verdict()),
                            compare.overallGainPercent(),
                            compare.completionRateDelta(),
                            compare.onTimeRateDelta(),
                            compare.cancellationRateDelta(),
                            compare.deadheadRatioDelta(),
                            compare.visibleBundleThreePlusRateDelta(),
                            compare.deliveryCorridorQualityDelta(),
                            compare.lastDropGoodZoneRateDelta(),
                            compare.expectedPostCompletionEmptyKmDelta(),
                            compare.realAssignmentRateDelta(),
                            compare.nonDowngradedRealAssignmentRateDelta(),
                            compare.waveAssemblyWaitRateDelta(),
                            compare.thirdOrderLaunchRateDelta(),
                            compare.cleanWaveRecoveryRateDelta(),
                            compare.stressDowngradeRateDelta(),
                            compare.prePickupAugmentRateDelta(),
                            compare.holdOnlySelectionRateDelta()));
            PlatformRuntimeBootstrap.recordReplayCompare(compare);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write replay compare artifact", e);
        }
    }

    private static void appendCsv(Path file, String header, String row) throws IOException {
        Files.createDirectories(file.getParent());
        if (Files.notExists(file)) {
            Files.writeString(
                    file,
                    header + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE
            );
        }
        Files.writeString(
                file,
                row + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace(",", "_");
    }
}
