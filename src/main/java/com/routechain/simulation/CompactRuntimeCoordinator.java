package com.routechain.simulation;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.core.AdaptiveWeightEngine;
import com.routechain.core.CompactCoreAdapter;
import com.routechain.core.CompactDecisionResolution;
import com.routechain.core.CompactDispatchDecision;
import com.routechain.core.CompactEvidenceBundle;
import com.routechain.core.CompactPolicyConfig;
import com.routechain.core.DecisionLogRecord;
import com.routechain.core.CompactSelectedPlanEvidence;
import com.routechain.core.DriftMonitor;
import com.routechain.core.WeightSnapshot;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.Order;
import com.routechain.domain.Region;
import com.routechain.infra.DispatchFactSink;
import com.routechain.infra.PlatformRuntimeBootstrap;
import com.routechain.simulation.DispatchPlan;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CompactRuntimeCoordinator {
    private final CompactPolicyConfig policyConfig;
    private final CompactCoreAdapter compactCoreAdapter;
    private final CompactLearningRuntime learningRuntime;
    private final CompactEvidencePublisher evidencePublisher = new CompactEvidencePublisher();
    private final DispatchFactSink dispatchFactSink = PlatformRuntimeBootstrap.getDispatchFactSink();
    private final Map<String, String> runIdsByTrace = new ConcurrentHashMap<>();

    public CompactRuntimeCoordinator() {
        this(CompactPolicyConfig.defaults(), RouteChainDispatchV2Properties.defaults());
    }

    public CompactRuntimeCoordinator(CompactPolicyConfig policyConfig) {
        this(policyConfig, RouteChainDispatchV2Properties.defaults());
    }

    public CompactRuntimeCoordinator(CompactPolicyConfig policyConfig,
                                     RouteChainDispatchV2Properties dispatchV2Properties) {
        this.policyConfig = policyConfig == null ? CompactPolicyConfig.defaults() : policyConfig;
        this.compactCoreAdapter = new CompactCoreAdapter(this.policyConfig, dispatchV2Properties);
        this.learningRuntime = new CompactLearningRuntime(this.policyConfig);
        this.compactCoreAdapter.core().syncLearningState(
                learningRuntime.latestSnapshotTag(),
                learningRuntime.rollbackAvailable());
    }

    public CompactDispatchDecision dispatch(List<Order> openOrders,
                                            List<Driver> availableDrivers,
                                            List<Region> regions,
                                            int simulatedHour,
                                            double trafficIntensity,
                                            WeatherProfile weatherProfile,
                                            Instant decisionTime) {
        return compactCoreAdapter.dispatch(
                openOrders,
                availableDrivers,
                regions,
                simulatedHour,
                trafficIntensity,
                weatherProfile,
                decisionTime);
    }

    public void beginDecision(String runId,
                              String modeName,
                              Instant decisionTime,
                              CompactDispatchDecision decision) {
        compactCoreAdapter.core().syncLearningState(
                learningRuntime.latestSnapshotTag(),
                learningRuntime.rollbackAvailable());
        evidencePublisher.publishDecision(
                runId,
                modeName,
                decisionTime,
                decision,
                compactCoreAdapter.core().currentWeightSnapshot(),
                learningRuntime.calibrationRuntime().snapshot(),
                compactCoreAdapter.core().latestSnapshotTag(),
                compactCoreAdapter.core().rollbackAvailable(),
                compactCoreAdapter.core().isLearningFrozen());
    }

    public void recordSelectedPlan(String runId,
                                   DispatchPlan executablePlan,
                                   CompactSelectedPlanEvidence evidence,
                                   WeightSnapshot snapshotBefore,
                                   Instant decisionTime) {
        DecisionLogRecord calibratedDecisionLog = learningRuntime.calibrationRuntime().calibrateDecisionLog(
                buildDecisionLogRecord(executablePlan, evidence, snapshotBefore, decisionTime));
        runIdsByTrace.put(calibratedDecisionLog.decisionId(), runId == null ? "compact-run-unset" : runId);
        learningRuntime.ledger().recordDecision(
                calibratedDecisionLog.decisionId(),
                calibratedDecisionLog.driverId(),
                calibratedDecisionLog.bundleId(),
                calibratedDecisionLog.planType(),
                calibratedDecisionLog.orderIds(),
                calibratedDecisionLog.featureVector(),
                calibratedDecisionLog.scoreBreakdown(),
                calibratedDecisionLog.snapshotBefore(),
                calibratedDecisionLog.decisionTime(),
                calibratedDecisionLog.predictedEtaMinutes(),
                calibratedDecisionLog.predictedDeadheadKm(),
                calibratedDecisionLog.predictedRevenue(),
                calibratedDecisionLog.predictedLandingScore(),
                calibratedDecisionLog.predictedPostDropDemandProbability(),
                calibratedDecisionLog.predictedPostCompletionEmptyKm(),
                calibratedDecisionLog.predictedNextOrderIdleMinutes(),
                calibratedDecisionLog.predictedCancelRisk(),
                calibratedDecisionLog.predictedOnTimeProbability(),
                calibratedDecisionLog.predictedTripDistanceKm());
        recordDecisionFact(runId, executablePlan, calibratedDecisionLog);
        resolve(learningRuntime.ledger().recordAccepted(calibratedDecisionLog.decisionId(), decisionTime), decisionTime);
    }

    public void recordOrderDelivered(String traceId,
                                     String orderId,
                                     boolean onTime,
                                     double fee,
                                     double actualEtaMinutes,
                                     Instant terminalAt) {
        resolve(learningRuntime.ledger().recordOrderDelivered(traceId, orderId, onTime, fee, actualEtaMinutes, terminalAt), terminalAt);
    }

    public void recordOrderCancelled(String traceId, String orderId, Instant terminalAt) {
        resolve(learningRuntime.ledger().recordOrderCancelled(traceId, orderId, terminalAt), terminalAt);
    }

    public void markDriverIdle(String driverId, long tick, Instant when, GeoPoint idleLocation) {
        learningRuntime.ledger().markDriverIdle(driverId, tick, when, idleLocation);
    }

    public void recordPostDropHit(String driverId, long tick, Instant when, GeoPoint nextPickupLocation) {
        CompactDecisionResolution resolution = learningRuntime.ledger().recordPostDropHit(driverId, tick, when, nextPickupLocation);
        resolve(resolution, when);
    }

    public void expire(long currentTick, Instant now) {
        for (CompactDecisionResolution resolution : learningRuntime.expire(currentTick, now)) {
            resolve(resolution, now);
        }
        learningRuntime.ledger().clearResolved();
    }

    public void reset() {
        learningRuntime.reset();
        evidencePublisher.reset();
        runIdsByTrace.clear();
        compactCoreAdapter.core().syncLearningState(
                learningRuntime.latestSnapshotTag(),
                learningRuntime.rollbackAvailable());
    }

    public CompactEvidenceBundle latestEvidence() {
        return evidencePublisher.latestEvidence();
    }

    public CompactRuntimeStatusView latestStatus() {
        return evidencePublisher.latestStatus();
    }

    public WeightSnapshot currentWeightSnapshot() {
        return compactCoreAdapter.core().currentWeightSnapshot();
    }

    public CompactPolicyConfig policyConfig() {
        return policyConfig;
    }

    public AdaptiveWeightEngine weightEngine() {
        return compactCoreAdapter.core().adaptiveWeightEngine();
    }

    private DecisionLogRecord buildDecisionLogRecord(DispatchPlan executablePlan,
                                                     CompactSelectedPlanEvidence evidence,
                                                     WeightSnapshot snapshotBefore,
                                                     Instant decisionTime) {
        return new DecisionLogRecord(
                executablePlan.getTraceId(),
                executablePlan.getDriver().getId(),
                executablePlan.getBundle().bundleId(),
                evidence.planType(),
                executablePlan.getOrders().stream().map(Order::getId).toList(),
                evidence.scoreBreakdown().regimeKey(),
                evidence.featureVector(),
                evidence.scoreBreakdown(),
                snapshotBefore,
                decisionTime,
                evidence.scoreBreakdown().finalScore(),
                com.routechain.core.RewardProjection.project(evidence.scoreBreakdown(), evidence.featureVector()),
                executablePlan.getPredictedTotalMinutes(),
                executablePlan.getPredictedDeadheadKm(),
                executablePlan.getCustomerFee(),
                executablePlan.getLastDropLandingScore(),
                executablePlan.getPostDropDemandProbability(),
                executablePlan.getExpectedPostCompletionEmptyKm(),
                executablePlan.getExpectedNextOrderIdleMinutes(),
                executablePlan.getCancellationRisk(),
                executablePlan.getOnTimeProbability(),
                estimateTripDistanceKm(executablePlan));
    }

    private double estimateTripDistanceKm(DispatchPlan executablePlan) {
        if (executablePlan == null || executablePlan.getSequence().isEmpty()) {
            return 0.0;
        }
        double totalMeters = 0.0;
        GeoPoint cursor = executablePlan.getDriver().getCurrentLocation();
        for (DispatchPlan.Stop stop : executablePlan.getSequence()) {
            if (cursor != null) {
                totalMeters += cursor.distanceTo(stop.location());
            }
            cursor = stop.location();
        }
        return Math.max(0.0, totalMeters / 1000.0);
    }

    private void resolve(CompactDecisionResolution resolution, Instant resolvedAt) {
        if (resolution == null) {
            return;
        }
        recordOutcomeFact(resolution);
        DriftMonitor.DriftAssessment assessment = learningRuntime.resolveAndApply(
                resolution,
                resolvedAt,
                compactCoreAdapter.core().adaptiveWeightEngine());
        compactCoreAdapter.core().syncLearningState(
                learningRuntime.latestSnapshotTag(),
                learningRuntime.rollbackAvailable());
        WeightSnapshot snapshotAfter = compactCoreAdapter.core().currentWeightSnapshot();
        CompactDecisionResolution finalized = resolution.withSnapshotAfter(snapshotAfter, resolvedAt);
        evidencePublisher.publishResolution(
                finalized,
                compactCoreAdapter.core().latestSnapshotTag(),
                compactCoreAdapter.core().rollbackAvailable(),
                compactCoreAdapter.core().isLearningFrozen(),
                learningRuntime.calibrationRuntime().snapshot(),
                assessment);
        if (finalized.isFinalResolution()) {
            runIdsByTrace.remove(finalized.traceId());
        }
    }

    private void recordDecisionFact(String runId,
                                    DispatchPlan executablePlan,
                                    DecisionLogRecord decisionLog) {
        if (dispatchFactSink == null || executablePlan == null || decisionLog == null) {
            return;
        }
        Map<String, Object> semanticPlanSummary = new LinkedHashMap<>();
        semanticPlanSummary.put("planType", decisionLog.planType().name());
        semanticPlanSummary.put("selectionBucket", executablePlan.getSelectionBucket().name());
        semanticPlanSummary.put("predictedEtaMinutes", decisionLog.predictedEtaMinutes());
        semanticPlanSummary.put("predictedPostDropDemandProbability", decisionLog.predictedPostDropDemandProbability());
        semanticPlanSummary.put("predictedPostCompletionEmptyKm", decisionLog.predictedPostCompletionEmptyKm());
        semanticPlanSummary.put("predictedNextOrderIdleMinutes", decisionLog.predictedNextOrderIdleMinutes());

        dispatchFactSink.recordDecision(new DispatchFactSink.DecisionFact(
                decisionLog.decisionId(),
                runId == null || runId.isBlank() ? "compact-run-unset" : runId,
                decisionLog.decisionTime() == null ? 0L : decisionLog.decisionTime().toEpochMilli(),
                decisionLog.driverId(),
                "COMPACT",
                "COMPACT_RUNTIME",
                "NONE",
                decisionLog.predictedUtilityRaw(),
                executablePlan.getConfidence(),
                executablePlan.getBundleSize(),
                semanticPlanSummary,
                Map.of("regime", decisionLog.regimeKey().name()),
                new double[0],
                decisionLog.featureVector().values(),
                decisionLog.planType().name() + " via " + executablePlan.getSelectionBucket().name(),
                "",
                0,
                "NOT_REQUESTED",
                "",
                "NONE",
                executablePlan.getServiceTier(),
                "COMPACT_RUNTIME",
                0L,
                executablePlan.getSelectionBucket().name(),
                executablePlan.getHoldRemainingCycles(),
                executablePlan.getMarginalDeadheadPerAddedOrder(),
                null,
                decisionLog.decisionTime(),
                decisionLog.predictedRewardNormalized(),
                decisionLog.predictedEtaMinutes(),
                decisionLog.predictedCancelRisk(),
                decisionLog.predictedPostDropDemandProbability(),
                decisionLog.predictedPostCompletionEmptyKm(),
                decisionLog.predictedNextOrderIdleMinutes(),
                decisionLog.predictedTripDistanceKm()));
    }

    private void recordOutcomeFact(CompactDecisionResolution resolution) {
        if (dispatchFactSink == null || resolution == null || resolution.resolvedSample() == null || resolution.decisionLog() == null) {
            return;
        }
        DecisionLogRecord decisionLog = resolution.decisionLog();
        dispatchFactSink.recordOutcome(new DispatchFactSink.OutcomeFact(
                resolution.traceId(),
                runIdsByTrace.getOrDefault(resolution.traceId(), "compact-run-unset"),
                resolution.resolvedAt() == null ? 0L : resolution.resolvedAt().toEpochMilli(),
                resolution.resolvedSample().actualReward(),
                resolution.resolvedSample().actualCancelled(),
                resolution.outcomeVector().onTime() < 0.999,
                decisionLog.predictedRevenue() * resolution.outcomeVector().profit(),
                decisionLog.predictedDeadheadKm(),
                decisionLog.predictedPostCompletionEmptyKm(),
                decisionLog.featureVector().bundleEfficiency(),
                resolution.orderIds().size(),
                resolution.regimeKey().name(),
                decisionLog.planType() == com.routechain.core.CompactPlanType.FALLBACK_LOCAL,
                resolution.outcomeVector().postDropQuality(),
                resolution.outcomeVector().completion(),
                resolution.outcomeVector().landing(),
                decisionLog.predictedLandingScore(),
                decisionLog.predictedPostDropDemandProbability(),
                decisionLog.predictedNextOrderIdleMinutes(),
                resolution.outcomeVector().profit(),
                resolution.resolvedAt(),
                resolution.outcomeStage() == null ? "" : resolution.outcomeStage().name(),
                safeFactDouble(resolution.resolvedSample().actualEtaMinutes()),
                resolution.resolvedSample().actualCancelled(),
                resolution.resolvedSample().actualPostDropHit(),
                safeFactDouble(resolution.resolvedSample().actualPostCompletionEmptyKm()),
                safeFactDouble(resolution.resolvedSample().actualNextOrderIdleMinutes())));
    }

    private double safeFactDouble(double value) {
        return Double.isFinite(value) ? value : -1.0;
    }
}
