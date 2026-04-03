package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.graph.FutureCellValue;
import com.routechain.graph.GraphAffinitySnapshot;
import com.routechain.graph.GraphExplanationTrace;
import com.routechain.infra.EventContractCatalog;
import com.routechain.infra.GsonSupport;
import com.routechain.infra.PlatformRuntimeBootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Writes benchmark artifacts to durable local storage for defense/demo use.
 */
public final class BenchmarkArtifactWriter {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path ROOT = Path.of("build", "routechain-apex", "benchmarks");
    private static final Path RUNS_DIR = ROOT.resolve("runs");
    private static final Path COMPARES_DIR = ROOT.resolve("compares");
    private static final Path MANIFESTS_DIR = ROOT.resolve("manifests");
    private static final Path STATS_DIR = ROOT.resolve("stats");
    private static final Path ABLATIONS_DIR = ROOT.resolve("ablations");
    private static final Path DRIFT_DIR = ROOT.resolve("drift");
    private static final Path LATENCY_DIR = ROOT.resolve("latency");
    private static final Path ACCEPTANCE_DIR = ROOT.resolve("acceptance");
    private static final Path CONTROL_ROOM_DIR = ROOT.resolve("control-room");
    private static final Path CONTROL_ROOM_FRAMES_DIR = CONTROL_ROOM_DIR.resolve("frames");
    private static final Path RUNS_CSV = ROOT.resolve("run_reports.csv");
    private static final Path COMPARES_CSV = ROOT.resolve("replay_compare_results.csv");
    private static final Path MANIFESTS_CSV = ROOT.resolve("benchmark_manifests.csv");
    private static final Path STATS_CSV = ROOT.resolve("benchmark_stats.csv");
    private static final Path ABLATIONS_CSV = ROOT.resolve("policy_ablations.csv");
    private static final Path DRIFT_CSV = ROOT.resolve("drift_snapshots.csv");
    private static final Path LATENCY_CSV = ROOT.resolve("latency_breakdown.csv");
    private static final Path ACCEPTANCE_CSV = ROOT.resolve("scenario_acceptance.csv");
    private static final Path CITY_TWIN_CSV = CONTROL_ROOM_DIR.resolve("city_twin_cells.csv");
    private static final Path FUTURE_CELL_VALUE_CSV = CONTROL_ROOM_DIR.resolve("future_cell_values.csv");
    private static final Path DRIVER_FUTURE_VALUE_CSV = CONTROL_ROOM_DIR.resolve("driver_future_values.csv");
    private static final Path GRAPH_AFFINITY_CSV = CONTROL_ROOM_DIR.resolve("graph_affinities.csv");
    private static final Path GRAPH_EXPLANATION_CSV = CONTROL_ROOM_DIR.resolve("graph_explanations.csv");
    private static final Path MARKETPLACE_EDGE_CSV = CONTROL_ROOM_DIR.resolve("marketplace_edges.csv");
    private static final Path RIDER_COPILOT_CSV = CONTROL_ROOM_DIR.resolve("rider_copilot.csv");
    private static final Path MODEL_PROMOTION_CSV = CONTROL_ROOM_DIR.resolve("model_promotion.csv");
    private static final Path POLICY_PROFILE_CSV = CONTROL_ROOM_DIR.resolve("policy_profile.csv");
    private static final Path FORECAST_DRIFT_CSV = CONTROL_ROOM_DIR.resolve("forecast_drift.csv");
    private static final Path CONTROL_ROOM_LATEST_JSON = CONTROL_ROOM_DIR.resolve("control_room_latest.json");
    private static final Path CONTROL_ROOM_LATEST_MD = CONTROL_ROOM_DIR.resolve("control_room_latest.md");
    private static final Path RUNTIME_SLO_JSON = ROOT.resolve("runtime_slo_summary.json");
    private static final Path MEMORY_GC_JSON = ROOT.resolve("memory_gc_summary.json");
    private static final Path ENVIRONMENT_JSON = ROOT.resolve("environment_manifest.json");

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
                    "runId,scenario,seed,completion,onTime,cancel,deadhead,visible3plus,corridor,goodLast,emptyKm,nextIdle,realAssign,steadyAssign,launch3,recover3,downgrade,augment,holdOnly,waveExec,holdConv,fallbackDirect,borrowedExec,avgAssignedDeadheadKm,deadheadPerCompletedOrderKm,postDropOrderHitRate,dispatchP95Ms,dispatchP99Ms,tickThroughputPerSec,businessScore,routingScore,networkScore,forecastScore,primaryVerdict,secondaryVerdict,measurementPass,performancePass,intelligencePass,safetyPass,dominantServiceTier,merchantPrepMaeMinutes,continuationCalibrationGap",
                    String.format(
                            "%s,%s,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.6f,%.6f,%.6f,%.6f,%s,%s,%s,%s,%s,%s,%s,%.3f,%.3f",
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
                            report.holdOnlySelectionRate(),
                            report.recovery().waveExecutionRate(),
                            report.recovery().holdConversionRate(),
                            report.recovery().fallbackDirectRate(),
                            report.recovery().borrowedSelectionRate(),
                            report.avgAssignedDeadheadKm(),
                            report.deadheadPerCompletedOrderKm(),
                            report.postDropOrderHitRate(),
                            report.latency().dispatchP95Ms(),
                            report.latency().dispatchP99Ms(),
                            report.latency().tickThroughputPerSec(),
                            report.intelligence().businessScore(),
                            report.intelligence().routingScore(),
                            report.intelligence().networkScore(),
                            report.intelligence().forecastScore(),
                            safe(report.intelligence().primaryVerdict()),
                            safe(report.intelligence().secondaryVerdict()),
                            Boolean.toString(report.acceptance().measurementPass()),
                            Boolean.toString(report.acceptance().performancePass()),
                            Boolean.toString(report.acceptance().intelligencePass()),
                            Boolean.toString(report.acceptance().safetyPass()),
                            safe(report.dominantServiceTier()),
                            report.forecastCalibrationSummary() == null ? 0.0 : report.forecastCalibrationSummary().merchantPrepMaeMinutes(),
                            report.forecastCalibrationSummary() == null ? 0.0 : report.forecastCalibrationSummary().continuationCalibrationGap()));
            writeLatencyBreakdown("run/" + report.runId(), report.latency());
            writeScenarioAcceptance(report.acceptance());
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
                    "runIdA,runIdB,scenarioA,scenarioB,verdict,gain,completionDelta,onTimeDelta,cancelDelta,deadheadDelta,visible3plusDelta,corridorDelta,goodLastDelta,emptyKmDelta,realAssignDelta,steadyAssignDelta,wait3Delta,launch3Delta,recover3Delta,downgradeDelta,augmentDelta,holdOnlyDelta,waveExecDelta,holdConvDelta,fallbackDirectDelta,borrowedExecDelta,deadheadPerCompletedOrderKmDelta,postDropOrderHitRateDelta,dispatchP95MsDelta,businessScoreDelta,routingScoreDelta,networkScoreDelta,forecastScoreDelta,overallPassA,overallPassB,dominantServiceTierA,dominantServiceTierB,merchantPrepMaeMinutesDelta,continuationCalibrationGapDelta",
                    String.format(
                            "%s,%s,%s,%s,%s,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d,%d,%.3f,%.3f,%.6f,%.6f,%.6f,%.6f,%.6f,%s,%s,%s,%s,%.3f,%.3f",
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
                            compare.holdOnlySelectionRateDelta(),
                            compare.recoveryDelta() == null ? 0 : compare.recoveryDelta().executedWaveCountDelta(),
                            compare.recoveryDelta() == null ? 0 : compare.recoveryDelta().holdConvertedToWaveCountDelta(),
                            compare.recoveryDelta() == null ? 0 : compare.recoveryDelta().executedFallbackCountDelta(),
                            compare.recoveryDelta() == null ? 0 : compare.recoveryDelta().executedBorrowedCountDelta(),
                            compare.deadheadPerCompletedOrderKmDelta(),
                            compare.postDropOrderHitRateDelta(),
                            compare.latencyDelta().dispatchP95MsDelta(),
                            compare.intelligenceDelta().businessScoreDelta(),
                            compare.intelligenceDelta().routingScoreDelta(),
                            compare.intelligenceDelta().networkScoreDelta(),
                            compare.intelligenceDelta().forecastScoreDelta(),
                            Boolean.toString(compare.acceptanceDelta().overallPassA()),
                            Boolean.toString(compare.acceptanceDelta().overallPassB()),
                            safe(compare.dominantServiceTierA()),
                            safe(compare.dominantServiceTierB()),
                            compare.forecastCalibrationSummaryDelta() == null ? 0.0 : compare.forecastCalibrationSummaryDelta().merchantPrepMaeMinutesDelta(),
                            compare.forecastCalibrationSummaryDelta() == null ? 0.0 : compare.forecastCalibrationSummaryDelta().continuationCalibrationGapDelta()));
            PlatformRuntimeBootstrap.recordReplayCompare(compare);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write replay compare artifact", e);
        }
    }

    public static void writeManifest(BenchmarkRunManifest manifest) {
        if (manifest == null) {
            return;
        }
        String manifestId = manifest.manifestId() == null || manifest.manifestId().isBlank()
                ? "manifest-" + DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                : manifest.manifestId();
        try {
            Files.createDirectories(MANIFESTS_DIR);
            Files.writeString(
                    MANIFESTS_DIR.resolve(safe(manifestId) + ".json"),
                    GSON.toJson(manifest),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            appendCsv(
                    MANIFESTS_CSV,
                    "schemaVersion,manifestId,batchName,createdAt,gitRevision,seeds,driverProfiles,environmentProfile,routeLatencyMode,warmupRuns,measurementRuns,timeLimitSeconds,computeBudgetMs,protocol,caseCount,policyCandidateCount,counterfactualSpecCount",
                    String.format(
                            "%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,%d,%d,%d,%s,%d,%d,%d",
                            safe(manifest.schemaVersion()),
                            safe(manifestId),
                            safe(manifest.batchName()),
                            safe(String.valueOf(manifest.createdAt())),
                            safe(manifest.gitRevision()),
                            safe(joinList(manifest.seeds())),
                            safe(joinList(manifest.driverProfiles())),
                            safe(manifest.environmentProfile() == null ? "" : manifest.environmentProfile().profileName()),
                            safe(manifest.environmentProfile() == null ? "" : manifest.environmentProfile().routeLatencyMode()),
                            manifest.warmupRuns(),
                            manifest.measurementRuns(),
                            manifest.timeLimitSeconds(),
                            manifest.computeBudgetMs(),
                            safe(manifest.protocol()),
                            manifest.cases() == null ? 0 : manifest.cases().size(),
                            manifest.policyCandidates() == null ? 0 : manifest.policyCandidates().size(),
                            manifest.counterfactualSpecs() == null ? 0 : manifest.counterfactualSpecs().size()
                    )
            );
            writeEnvironmentManifest(manifest.environmentProfile());
            PlatformRuntimeBootstrap.getCanonicalEventPublisher().publish(
                    EventContractCatalog.BENCHMARK_MANIFEST_V2,
                    manifest);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write benchmark manifest artifact", e);
        }
    }

    public static void writeStatSummary(BenchmarkStatSummary summary) {
        if (summary == null) {
            return;
        }
        try {
            Files.createDirectories(STATS_DIR);
            String fileName = safe(summary.scope() + "-" + summary.metricName()) + ".json";
            Files.writeString(
                    STATS_DIR.resolve(fileName),
                    GSON.toJson(summary),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            appendCsv(
                    STATS_CSV,
                    "schemaVersion,metricName,metricClass,scope,sampleCount,mean,median,p95,stdDev,ci95Low,ci95High,effectSizeCohensD,pValue,significantAt95",
                    String.format(
                            "%s,%s,%s,%s,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%s,%s,%s",
                            safe(summary.schemaVersion()),
                            safe(summary.metricName()),
                            safe(summary.metricClass()),
                            safe(summary.scope()),
                            summary.sampleCount(),
                            summary.mean(),
                            summary.median(),
                            summary.p95(),
                            summary.stdDev(),
                            summary.ci95Low(),
                            summary.ci95High(),
                            summary.effectSizeCohensD() == null ? "" : String.format("%.6f", summary.effectSizeCohensD()),
                            summary.pValue() == null ? "" : String.format("%.6f", summary.pValue()),
                            summary.significantAt95() == null ? "" : summary.significantAt95().toString()
                    )
            );
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write benchmark stat summary artifact", e);
        }
    }

    public static void writeAblationResult(PolicyAblationResult result) {
        if (result == null) {
            return;
        }
        String ablationId = result.ablationId() == null || result.ablationId().isBlank()
                ? "ablation-" + DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                : result.ablationId();
        try {
            Files.createDirectories(ABLATIONS_DIR);
            Files.writeString(
                    ABLATIONS_DIR.resolve(safe(ablationId) + ".json"),
                    GSON.toJson(result),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            appendCsv(
                    ABLATIONS_CSV,
                    "schemaVersion,ablationId,scenarioName,baselinePolicy,candidatePolicy,verdict,overallGainPercent,gainMean,gainCI95Low,gainCI95High,completionDeltaMean,deadheadDeltaMean",
                    String.format(
                            "%s,%s,%s,%s,%s,%s,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f",
                            safe(result.schemaVersion()),
                            safe(ablationId),
                            safe(result.scenarioName()),
                            safe(result.baselinePolicy()),
                            safe(result.candidatePolicy()),
                            safe(result.verdict()),
                            result.overallGainPercent(),
                            result.gainSummary() == null ? 0.0 : result.gainSummary().mean(),
                            result.gainSummary() == null ? 0.0 : result.gainSummary().ci95Low(),
                            result.gainSummary() == null ? 0.0 : result.gainSummary().ci95High(),
                            result.completionDeltaSummary() == null ? 0.0 : result.completionDeltaSummary().mean(),
                            result.deadheadDeltaSummary() == null ? 0.0 : result.deadheadDeltaSummary().mean()
                    )
            );
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write policy ablation artifact", e);
        }
    }

    public static void writeDriftSnapshot(DispatchDriftSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        try {
            Files.createDirectories(DRIFT_DIR);
            String fileName = safe(snapshot.scope() + "-" + snapshot.metricName()) + ".json";
            Files.writeString(
                    DRIFT_DIR.resolve(fileName),
                    GSON.toJson(snapshot),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            appendCsv(
                    DRIFT_CSV,
                    "schemaVersion,scope,metricName,baselineMean,candidateMean,drift,threshold,drifted",
                    String.format(
                            "%s,%s,%s,%.6f,%.6f,%.6f,%.6f,%s",
                            safe(snapshot.schemaVersion()),
                            safe(snapshot.scope()),
                            safe(snapshot.metricName()),
                            snapshot.baselineMean(),
                            snapshot.candidateMean(),
                            snapshot.drift(),
                            snapshot.threshold(),
                            snapshot.drifted()
                    )
            );
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write drift snapshot artifact", e);
        }
    }

    public static void writeLatencyBreakdown(String scope, LatencyBreakdown latency) {
        if (latency == null) {
            return;
        }
        try {
            Files.createDirectories(LATENCY_DIR);
            Files.writeString(
                    LATENCY_DIR.resolve(safe(scope) + ".json"),
                    GSON.toJson(latency),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            appendCsv(
                    LATENCY_CSV,
                    "scope,dispatchP50Ms,dispatchP95Ms,dispatchP99Ms,modelP50Ms,modelP95Ms,neuralPriorP50Ms,neuralPriorP95Ms,assignmentAgingP50Ms,assignmentAgingP95Ms,tickThroughputPerSec,dispatchSampleCount,assignmentSampleCount",
                    String.format(
                            "%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d",
                            safe(scope),
                            latency.dispatchP50Ms(),
                            latency.dispatchP95Ms(),
                            latency.dispatchP99Ms(),
                            latency.modelP50Ms(),
                            latency.modelP95Ms(),
                            latency.neuralPriorP50Ms(),
                            latency.neuralPriorP95Ms(),
                            latency.assignmentAgingP50Ms(),
                            latency.assignmentAgingP95Ms(),
                            latency.tickThroughputPerSec(),
                            latency.dispatchSampleCount(),
                            latency.assignmentSampleCount()
                    )
            );
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write latency breakdown artifact", e);
        }
    }

    public static void writeScenarioAcceptance(ScenarioAcceptanceResult acceptance) {
        if (acceptance == null) {
            return;
        }
        try {
            Files.createDirectories(ACCEPTANCE_DIR);
            Files.writeString(
                    ACCEPTANCE_DIR.resolve(safe(acceptance.scenarioName() + "-" + acceptance.serviceTier()) + ".json"),
                    GSON.toJson(acceptance),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            appendCsv(
                    ACCEPTANCE_CSV,
                    "scenarioName,serviceTier,environmentProfile,measurementPass,performancePass,intelligencePass,safetyPass,overallPass,primaryVerdict,secondaryVerdict,notes",
                    String.format(
                            "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                            safe(acceptance.scenarioName()),
                            safe(acceptance.serviceTier()),
                            safe(acceptance.environmentProfile()),
                            Boolean.toString(acceptance.measurementPass()),
                            Boolean.toString(acceptance.performancePass()),
                            Boolean.toString(acceptance.intelligencePass()),
                            Boolean.toString(acceptance.safetyPass()),
                            Boolean.toString(acceptance.overallPass()),
                            safe(acceptance.primaryVerdict()),
                            safe(acceptance.secondaryVerdict()),
                            safe(acceptance.notes())
                    )
            );
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write scenario acceptance artifact", e);
        }
    }

    public static void writeControlRoomFrame(ControlRoomFrame frame) {
        if (frame == null) {
            return;
        }
        try {
            Files.createDirectories(CONTROL_ROOM_FRAMES_DIR);
            Path jsonFile = CONTROL_ROOM_FRAMES_DIR.resolve(safe(frame.runId()) + ".json");
            Path markdownFile = CONTROL_ROOM_FRAMES_DIR.resolve(safe(frame.runId()) + ".md");
            String json = GSON.toJson(frame);
            String markdown = renderControlRoomMarkdown(frame);
            Files.writeString(jsonFile, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(markdownFile, markdown, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(CONTROL_ROOM_LATEST_JSON, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(CONTROL_ROOM_LATEST_MD, markdown, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            appendCsv(
                    POLICY_PROFILE_CSV,
                    "runId,scenario,policyName,serviceTier,executionProfile,routeLatencyMode,gateProfile,reserveProfile,contextualBanditMode,explorationRate,shortageRatio,avgPendingWaitMinutes,surgeLevel,trafficIntensity,weatherProfile",
                    String.format(
                            "%s,%s,%s,%s,%s,%s,%s,%s,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%s",
                            safe(frame.runId()),
                            safe(frame.scenarioName()),
                            safe(frame.routePolicyProfile().policyName()),
                            safe(frame.routePolicyProfile().serviceTier()),
                            safe(frame.routePolicyProfile().executionProfile()),
                            safe(frame.routePolicyProfile().routeLatencyMode()),
                            safe(frame.routePolicyProfile().gateProfile()),
                            safe(frame.routePolicyProfile().reserveProfile()),
                            safe(frame.routePolicyProfile().contextualBanditMode()),
                            frame.routePolicyProfile().explorationRate(),
                            frame.routePolicyProfile().shortageRatio(),
                            frame.routePolicyProfile().avgPendingWaitMinutes(),
                            frame.routePolicyProfile().surgeLevel(),
                            frame.routePolicyProfile().trafficIntensity(),
                            safe(frame.routePolicyProfile().weatherProfile())
                    )
            );
            appendCsv(
                    FORECAST_DRIFT_CSV,
                    "runId,scenario,continuationCalibrationGap,merchantPrepMaeMinutes,trafficForecastError,weatherForecastHitRate,borrowSuccessCalibration,drifted,verdict,note",
                    String.format(
                            "%s,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%s,%s,%s",
                            safe(frame.runId()),
                            safe(frame.scenarioName()),
                            frame.forecastDrift().continuationCalibrationGap(),
                            frame.forecastDrift().merchantPrepMaeMinutes(),
                            frame.forecastDrift().trafficForecastError(),
                            frame.forecastDrift().weatherForecastHitRate(),
                            frame.forecastDrift().borrowSuccessCalibration(),
                            Boolean.toString(frame.forecastDrift().drifted()),
                            safe(frame.forecastDrift().verdict()),
                            safe(frame.forecastDrift().note())
                    )
            );
            for (CellValueSnapshot cell : frame.cityTwinCells()) {
                appendCsv(
                        CITY_TWIN_CSV,
                        "runId,scenario,cellId,spatialIndex,serviceTier,centerLat,centerLng,currentDemand,demand5m,demand10m,demand15m,demand30m,shortage10m,traffic10m,weather10m,postDrop10m,emptyRisk10m,reserveTargetScore,borrowPressure,compositeValue",
                        String.format(
                                "%s,%s,%s,%s,%s,%.6f,%.6f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f",
                                safe(frame.runId()),
                                safe(frame.scenarioName()),
                                safe(cell.cellId()),
                                safe(cell.spatialIndex()),
                                safe(cell.serviceTier()),
                                cell.centerLat(),
                                cell.centerLng(),
                                cell.currentDemand(),
                                cell.demandForecast5m(),
                                cell.demandForecast10m(),
                                cell.demandForecast15m(),
                                cell.demandForecast30m(),
                                cell.shortageForecast10m(),
                                cell.trafficForecast10m(),
                                cell.weatherForecast10m(),
                                cell.postDropOpportunity10m(),
                                cell.emptyZoneRisk10m(),
                                cell.reserveTargetScore(),
                                cell.borrowPressure(),
                                cell.compositeValue()
                        )
                );
            }
            for (DriverFutureValue futureValue : frame.driverFutureValues()) {
                appendCsv(
                        DRIVER_FUTURE_VALUE_CSV,
                        "runId,scenario,driverId,currentCellId,targetCellId,serviceTier,horizonMinutes,currentZoneValue,targetZoneValue,postDropOpportunity,emptyZoneRisk,reserveSupport,futureValueScore,recommendation",
                        String.format(
                                "%s,%s,%s,%s,%s,%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%s",
                                safe(frame.runId()),
                                safe(frame.scenarioName()),
                                safe(futureValue.driverId()),
                                safe(futureValue.currentCellId()),
                                safe(futureValue.targetCellId()),
                                safe(futureValue.serviceTier()),
                                futureValue.horizonMinutes(),
                                futureValue.currentZoneValue(),
                                futureValue.targetZoneValue(),
                                futureValue.postDropOpportunity(),
                                futureValue.emptyZoneRisk(),
                                futureValue.reserveSupport(),
                                futureValue.futureValueScore(),
                                safe(futureValue.recommendation())
                        )
                );
            }
            for (FutureCellValue futureCellValue : frame.futureCellValues()) {
                appendCsv(
                        FUTURE_CELL_VALUE_CSV,
                        "runId,scenario,cellId,serviceTier,horizonMinutes,demandScore,postDropOpportunity,emptyRisk,graphCentralityScore,futureValueScore,rationale",
                        String.format(
                                "%s,%s,%s,%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%s",
                                safe(frame.runId()),
                                safe(frame.scenarioName()),
                                safe(futureCellValue.cellId()),
                                safe(futureCellValue.serviceTier()),
                                futureCellValue.horizonMinutes(),
                                futureCellValue.demandScore(),
                                futureCellValue.postDropOpportunity(),
                                futureCellValue.emptyRisk(),
                                futureCellValue.graphCentralityScore(),
                                futureCellValue.futureValueScore(),
                                safe(futureCellValue.rationale())
                        )
                );
            }
            for (GraphAffinitySnapshot affinity : frame.graphAffinities()) {
                appendCsv(
                        GRAPH_AFFINITY_CSV,
                        "runId,scenario,relationType,sourceType,sourceId,sourceCellId,targetType,targetId,targetCellId,affinityScore,explanation",
                        String.format(
                                "%s,%s,%s,%s,%s,%s,%s,%s,%s,%.4f,%s",
                                safe(frame.runId()),
                                safe(frame.scenarioName()),
                                safe(affinity.relationType()),
                                safe(affinity.source().nodeType()),
                                safe(affinity.source().nodeId()),
                                safe(affinity.source().cellId()),
                                safe(affinity.target().nodeType()),
                                safe(affinity.target().nodeId()),
                                safe(affinity.target().cellId()),
                                affinity.affinityScore(),
                                safe(affinity.explanation())
                        )
                );
            }
            for (GraphExplanationTrace trace : frame.graphExplanations()) {
                appendCsv(
                        GRAPH_EXPLANATION_CSV,
                        "runId,scenario,traceId,driverId,orderKey,sourceCellId,targetCellId,graphAffinityScore,topologyScore,bundleCompatibilityScore,futureCellScore,congestionPropagationScore,explanation",
                        String.format(
                                "%s,%s,%s,%s,%s,%s,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%s",
                                safe(frame.runId()),
                                safe(frame.scenarioName()),
                                safe(trace.traceId()),
                                safe(trace.driverId()),
                                safe(trace.orderKey()),
                                safe(trace.sourceCellId()),
                                safe(trace.targetCellId()),
                                trace.graphAffinityScore(),
                                trace.topologyScore(),
                                trace.bundleCompatibilityScore(),
                                trace.futureCellScore(),
                                trace.congestionPropagationScore(),
                                safe(trace.explanation())
                        )
                );
            }
            for (MarketplaceEdge edge : frame.marketplaceEdges()) {
                appendCsv(
                        MARKETPLACE_EDGE_CSV,
                        "runId,scenario,edgeId,driverId,orderId,serviceTier,pickupCellId,dropoffCellId,pickupEtaMinutes,deadheadKm,executionScore,continuationScore,graphAffinityScore,edgeScore,borrowed,rationale,graphExplanation",
                        String.format(
                                "%s,%s,%s,%s,%s,%s,%s,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%s,%s,%s",
                                safe(frame.runId()),
                                safe(frame.scenarioName()),
                                safe(edge.edgeId()),
                                safe(edge.driverId()),
                                safe(edge.orderId()),
                                safe(edge.serviceTier()),
                                safe(edge.pickupCellId()),
                                safe(edge.dropoffCellId()),
                                edge.pickupEtaMinutes(),
                                edge.deadheadKm(),
                                edge.executionScore(),
                                edge.continuationScore(),
                                edge.graphAffinityScore(),
                                edge.edgeScore(),
                                Boolean.toString(edge.borrowed()),
                                safe(edge.rationale()),
                                safe(edge.graphExplanationTrace().explanation())
                        )
                );
            }
            for (RiderCopilotRecommendation recommendation : frame.riderCopilot()) {
                appendCsv(
                        RIDER_COPILOT_CSV,
                        "runId,scenario,recommendationId,driverSegment,serviceTier,action,targetCellId,targetLat,targetLng,priorityScore,continuationOpportunity,emptyZoneRisk,reserveSupport,reason",
                        String.format(
                                "%s,%s,%s,%s,%s,%s,%s,%.6f,%.6f,%.4f,%.4f,%.4f,%.4f,%s",
                                safe(frame.runId()),
                                safe(frame.scenarioName()),
                                safe(recommendation.recommendationId()),
                                safe(recommendation.driverSegment()),
                                safe(recommendation.serviceTier()),
                                safe(recommendation.action()),
                                safe(recommendation.targetCellId()),
                                recommendation.targetLat(),
                                recommendation.targetLng(),
                                recommendation.priorityScore(),
                                recommendation.continuationOpportunity(),
                                recommendation.emptyZoneRisk(),
                                recommendation.reserveSupport(),
                                safe(recommendation.reason())
                        )
                );
            }
            for (ModelPromotionDecision promotion : frame.modelPromotions()) {
                appendCsv(
                        MODEL_PROMOTION_CSV,
                        "runId,scenario,modelKey,championVersion,challengerVersion,decision,reason,promoteNow,latencyBudgetMs,observedDispatchP95Ms,businessScore,calibrationGap",
                        String.format(
                                "%s,%s,%s,%s,%s,%s,%s,%s,%d,%.4f,%.4f,%.4f",
                                safe(frame.runId()),
                                safe(frame.scenarioName()),
                                safe(promotion.modelKey()),
                                safe(promotion.championVersion()),
                                safe(promotion.challengerVersion()),
                                safe(promotion.decision()),
                                safe(promotion.reason()),
                                Boolean.toString(promotion.promoteNow()),
                                promotion.latencyBudgetMs(),
                                promotion.observedDispatchP95Ms(),
                                promotion.businessScore(),
                                promotion.calibrationGap()
                        )
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write control-room artifact", e);
        }
    }

    public static void writeRuntimeSloSummary(RuntimeSloSummary summary) {
        writeSingletonJson(RUNTIME_SLO_JSON, summary, "runtime SLO summary");
    }

    public static void writeMemoryGcSummary(MemoryGcSummary summary) {
        writeSingletonJson(MEMORY_GC_JSON, summary, "memory/GC summary");
    }

    public static void writeEnvironmentManifest(BenchmarkEnvironmentProfile profile) {
        writeSingletonJson(ENVIRONMENT_JSON, profile, "environment manifest");
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
        if (value == null) {
            return "";
        }
        return value
                .replace(",", "_")
                .replace("/", "_")
                .replace("\\", "_")
                .replace(":", "_")
                .replace(" ", "_");
    }

    private static String joinList(java.util.List<?> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append('|');
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private static void writeSingletonJson(Path file, Object payload, String label) {
        if (payload == null) {
            return;
        }
        try {
            Files.createDirectories(ROOT);
            Files.writeString(
                    file,
                    GSON.toJson(payload),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write " + label + " artifact", e);
        }
    }

    private static String renderControlRoomMarkdown(ControlRoomFrame frame) {
        StringBuilder builder = new StringBuilder();
        builder.append("# RouteChain Control Room").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append(frame.summaryHeadline()).append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("## Scorecard").append(System.lineSeparator());
        builder.append("- Business verdict: ").append(frame.intelligence().primaryVerdict()).append(System.lineSeparator());
        builder.append("- Balanced verdict: ").append(frame.intelligence().secondaryVerdict()).append(System.lineSeparator());
        builder.append("- Dispatch P95/P99: ")
                .append(String.format("%.1fms / %.1fms", frame.latency().dispatchP95Ms(), frame.latency().dispatchP99Ms()))
                .append(System.lineSeparator());
        builder.append("- Acceptance: measurement=").append(frame.acceptance().measurementPass())
                .append(" performance=").append(frame.acceptance().performancePass())
                .append(" intelligence=").append(frame.acceptance().intelligencePass())
                .append(" safety=").append(frame.acceptance().safetyPass())
                .append(System.lineSeparator()).append(System.lineSeparator());

        builder.append("## City Twin").append(System.lineSeparator());
        for (CellValueSnapshot cell : frame.cityTwinCells()) {
            builder.append("- ").append(cell.cellId())
                    .append(" demand10=").append(String.format("%.2f", cell.demandForecast10m()))
                    .append(" postDrop=").append(String.format("%.2f", cell.postDropOpportunity10m()))
                    .append(" emptyRisk=").append(String.format("%.2f", cell.emptyZoneRisk10m()))
                    .append(" composite=").append(String.format("%.2f", cell.compositeValue()))
                    .append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());

        builder.append("## Driver Future Value").append(System.lineSeparator());
        for (DriverFutureValue futureValue : frame.driverFutureValues()) {
            builder.append("- ").append(futureValue.driverId())
                    .append(" -> ").append(futureValue.targetCellId())
                    .append(" (future=").append(String.format("%.2f", futureValue.futureValueScore())).append(") ")
                    .append(futureValue.recommendation())
                    .append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());

        builder.append("## Future Cell Values").append(System.lineSeparator());
        for (FutureCellValue futureCellValue : frame.futureCellValues()) {
            builder.append("- ").append(futureCellValue.cellId())
                    .append(" future=").append(String.format("%.2f", futureCellValue.futureValueScore()))
                    .append(" centrality=").append(String.format("%.2f", futureCellValue.graphCentralityScore()))
                    .append(" postDrop=").append(String.format("%.2f", futureCellValue.postDropOpportunity()))
                    .append(" :: ").append(futureCellValue.rationale())
                    .append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());

        builder.append("## Marketplace Edges").append(System.lineSeparator());
        for (MarketplaceEdge edge : frame.marketplaceEdges()) {
            builder.append("- ").append(edge.driverId())
                    .append(" -> ").append(edge.orderId())
                    .append(" score=").append(String.format("%.2f", edge.edgeScore()))
                    .append(" exec=").append(String.format("%.2f", edge.executionScore()))
                    .append(" cont=").append(String.format("%.2f", edge.continuationScore()))
                    .append(" graph=").append(String.format("%.2f", edge.graphAffinityScore()))
                    .append(" borrowed=").append(edge.borrowed())
                    .append(" :: ").append(edge.rationale())
                    .append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());

        builder.append("## Graph Affinities").append(System.lineSeparator());
        for (GraphAffinitySnapshot affinity : frame.graphAffinities().stream().limit(8).toList()) {
            builder.append("- ").append(affinity.relationType())
                    .append(" ").append(affinity.source().nodeId())
                    .append(" -> ").append(affinity.target().nodeId())
                    .append(" score=").append(String.format("%.2f", affinity.affinityScore()))
                    .append(" :: ").append(affinity.explanation())
                    .append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());

        builder.append("## Graph Explanations").append(System.lineSeparator());
        for (GraphExplanationTrace trace : frame.graphExplanations().stream().limit(8).toList()) {
            builder.append("- ").append(trace.driverId())
                    .append(" / ").append(trace.orderKey())
                    .append(" graph=").append(String.format("%.2f", trace.graphAffinityScore()))
                    .append(" topology=").append(String.format("%.2f", trace.topologyScore()))
                    .append(" future=").append(String.format("%.2f", trace.futureCellScore()))
                    .append(" :: ").append(trace.explanation())
                    .append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());

        builder.append("## Rider Copilot").append(System.lineSeparator());
        for (RiderCopilotRecommendation recommendation : frame.riderCopilot()) {
            builder.append("- ").append(recommendation.action())
                    .append(" -> ").append(recommendation.targetCellId())
                    .append(" (priority=").append(String.format("%.2f", recommendation.priorityScore())).append(") ")
                    .append(recommendation.reason())
                    .append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());

        builder.append("## Model Ops").append(System.lineSeparator());
        for (ModelPromotionDecision promotion : frame.modelPromotions()) {
            builder.append("- ").append(promotion.modelKey())
                    .append(": champion=").append(promotion.championVersion());
            if (!promotion.challengerVersion().isBlank()) {
                builder.append(", challenger=").append(promotion.challengerVersion());
            }
            builder.append(", decision=").append(promotion.decision())
                    .append(", reason=").append(promotion.reason())
                    .append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());

        builder.append("## Evidence").append(System.lineSeparator());
        for (String evidence : frame.evidence()) {
            builder.append("- ").append(evidence).append(System.lineSeparator());
        }
        return builder.toString();
    }
}
