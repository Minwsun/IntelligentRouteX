package com.routechain.simulation;

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
import com.routechain.simulation.DispatchPlan;

import java.time.Instant;
import java.util.List;

public class CompactRuntimeCoordinator {
    private final CompactPolicyConfig policyConfig;
    private final CompactCoreAdapter compactCoreAdapter;
    private final CompactLearningRuntime learningRuntime;
    private final CompactEvidencePublisher evidencePublisher = new CompactEvidencePublisher();

    public CompactRuntimeCoordinator() {
        this(CompactPolicyConfig.defaults());
    }

    public CompactRuntimeCoordinator(CompactPolicyConfig policyConfig) {
        this.policyConfig = policyConfig == null ? CompactPolicyConfig.defaults() : policyConfig;
        this.compactCoreAdapter = new CompactCoreAdapter(this.policyConfig);
        this.learningRuntime = new CompactLearningRuntime(this.policyConfig);
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
        evidencePublisher.publishDecision(
                runId,
                modeName,
                decisionTime,
                decision,
                compactCoreAdapter.core().adaptiveWeightEngine().snapshot(),
                learningRuntime.calibrationRuntime().snapshot(),
                learningRuntime.latestSnapshotTag(),
                learningRuntime.rollbackAvailable(),
                compactCoreAdapter.core().adaptiveWeightEngine().isLearningFrozen());
    }

    public void recordSelectedPlan(DispatchPlan executablePlan,
                                   CompactSelectedPlanEvidence evidence,
                                   WeightSnapshot snapshotBefore,
                                   Instant decisionTime) {
        DecisionLogRecord calibratedDecisionLog = learningRuntime.calibrationRuntime().calibrateDecisionLog(
                buildDecisionLogRecord(executablePlan, evidence, snapshotBefore, decisionTime));
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
    }

    public void recordOrderDelivered(String traceId, String orderId, boolean onTime, double fee, double actualEtaMinutes) {
        learningRuntime.ledger().recordOrderDelivered(traceId, orderId, onTime, fee, actualEtaMinutes);
    }

    public void recordOrderCancelled(String traceId, String orderId) {
        learningRuntime.ledger().recordOrderCancelled(traceId, orderId);
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
    }

    public CompactEvidenceBundle latestEvidence() {
        return evidencePublisher.latestEvidence();
    }

    public CompactRuntimeStatusView latestStatus() {
        return evidencePublisher.latestStatus();
    }

    public WeightSnapshot currentWeightSnapshot() {
        return compactCoreAdapter.core().adaptiveWeightEngine().snapshot();
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
        DriftMonitor.DriftAssessment assessment = learningRuntime.resolveAndApply(
                resolution,
                resolvedAt,
                compactCoreAdapter.core().adaptiveWeightEngine());
        WeightSnapshot snapshotAfter = compactCoreAdapter.core().adaptiveWeightEngine().snapshot();
        CompactDecisionResolution finalized = resolution.withSnapshotAfter(snapshotAfter, resolvedAt);
        evidencePublisher.publishResolution(
                finalized,
                learningRuntime.latestSnapshotTag(),
                learningRuntime.rollbackAvailable(),
                compactCoreAdapter.core().adaptiveWeightEngine().isLearningFrozen(),
                learningRuntime.calibrationRuntime().snapshot(),
                assessment);
    }
}
