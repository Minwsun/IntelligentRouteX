package com.routechain.core;

import com.routechain.domain.GeoPoint;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CompactDecisionLedger {
    private final Map<String, Entry> entriesByTrace = new LinkedHashMap<>();
    private final Map<String, String> traceByDriver = new LinkedHashMap<>();

    public void recordDecision(String traceId,
                               String driverId,
                               String bundleId,
                               CompactPlanType planType,
                               List<String> orderIds,
                               PlanFeatureVector featureVector,
                               AdaptiveScoreBreakdown scoreBreakdown,
                               WeightSnapshot snapshotBefore,
                               Instant decisionTime,
                               double predictedEtaMinutes,
                               double plannedDeadheadKm,
                               double plannedRevenue,
                               double plannedLandingScore,
                               double predictedPostDropDemandProbability,
                               double plannedEmptyKm,
                               double predictedNextOrderIdleMinutes,
                               double predictedCancelRisk,
                               double predictedOnTimeProbability,
                               double predictedTripDistanceKm) {
        Entry entry = new Entry(
                traceId,
                driverId,
                bundleId,
                planType,
                new LinkedHashSet<>(orderIds),
                featureVector,
                scoreBreakdown,
                snapshotBefore,
                decisionTime,
                predictedEtaMinutes,
                plannedDeadheadKm,
                plannedRevenue,
                plannedLandingScore,
                predictedPostDropDemandProbability,
                plannedEmptyKm,
                predictedNextOrderIdleMinutes,
                predictedCancelRisk,
                predictedOnTimeProbability,
                predictedTripDistanceKm);
        entriesByTrace.put(traceId, entry);
        traceByDriver.put(driverId, traceId);
    }

    public void recordOrderDelivered(String traceId,
                                     String orderId,
                                     boolean onTime,
                                     double fee,
                                     double actualEtaMinutes) {
        Entry entry = entriesByTrace.get(traceId);
        if (entry == null || entry.resolved) {
            return;
        }
        if (entry.orderIds.contains(orderId)) {
            entry.deliveredOrderIds.add(orderId);
            if (onTime) {
                entry.onTimeDeliveries++;
            }
            entry.realizedRevenue += fee;
            if (actualEtaMinutes > 0.0) {
                entry.actualEtaMinutesSum += actualEtaMinutes;
                entry.actualEtaSamples++;
            }
        }
    }

    public void recordOrderCancelled(String traceId, String orderId) {
        Entry entry = entriesByTrace.get(traceId);
        if (entry == null || entry.resolved) {
            return;
        }
        if (entry.orderIds.contains(orderId)) {
            entry.cancelledOrderIds.add(orderId);
        }
    }

    public void markDriverIdle(String driverId, long tick, Instant when, GeoPoint idleLocation) {
        Entry entry = entryForDriver(driverId);
        if (entry == null || entry.resolved) {
            return;
        }
        if (entry.isTerminal()) {
            entry.awaitingPostDrop = true;
            entry.idleTick = tick;
            entry.idleAt = when;
            entry.idleLocation = idleLocation;
        }
    }

    public CompactDecisionResolution recordPostDropHit(String driverId,
                                                       long tick,
                                                       Instant when,
                                                       GeoPoint nextPickupLocation) {
        Entry entry = entryForDriver(driverId);
        if (entry == null || entry.resolved || !entry.awaitingPostDrop) {
            return null;
        }
        entry.postDropHit = true;
        entry.actualPostCompletionEmptyKm = distanceKm(entry.idleLocation, nextPickupLocation);
        entry.actualNextOrderIdleMinutes = durationMinutes(entry.idleAt, when);
        entry.resolved = true;
        traceByDriver.remove(driverId);
        return entry.toResolution(when);
    }

    public List<CompactDecisionResolution> expirePostDrop(long currentTick,
                                                          Instant when,
                                                          long hitWindowTicks) {
        List<CompactDecisionResolution> expired = new ArrayList<>();
        for (Entry entry : entriesByTrace.values()) {
            if (entry.resolved || !entry.awaitingPostDrop) {
                continue;
            }
            if (entry.idleTick >= 0 && currentTick - entry.idleTick > hitWindowTicks) {
                entry.actualPostCompletionEmptyKm = Double.NaN;
                entry.actualNextOrderIdleMinutes = durationMinutes(entry.idleAt, when);
                entry.resolved = true;
                traceByDriver.remove(entry.driverId);
                expired.add(entry.toResolution(when));
            }
        }
        return expired;
    }

    public void clearResolved() {
        entriesByTrace.entrySet().removeIf(entry -> entry.getValue().resolved);
    }

    public void reset() {
        entriesByTrace.clear();
        traceByDriver.clear();
    }

    private Entry entryForDriver(String driverId) {
        String traceId = traceByDriver.get(driverId);
        return traceId == null ? null : entriesByTrace.get(traceId);
    }

    private static final class Entry {
        private final String traceId;
        private final String driverId;
        private final String bundleId;
        private final CompactPlanType planType;
        private final Set<String> orderIds;
        private final PlanFeatureVector featureVector;
        private final AdaptiveScoreBreakdown scoreBreakdown;
        private final WeightSnapshot snapshotBefore;
        private final Instant decisionTime;
        private final double predictedEtaMinutes;
        private final double plannedDeadheadKm;
        private final double plannedRevenue;
        private final double plannedLandingScore;
        private final double predictedPostDropDemandProbability;
        private final double plannedEmptyKm;
        private final double predictedNextOrderIdleMinutes;
        private final double predictedCancelRisk;
        private final double predictedOnTimeProbability;
        private final double predictedTripDistanceKm;
        private final Set<String> deliveredOrderIds = new LinkedHashSet<>();
        private final Set<String> cancelledOrderIds = new LinkedHashSet<>();
        private double realizedRevenue = 0.0;
        private int onTimeDeliveries = 0;
        private double actualEtaMinutesSum = 0.0;
        private int actualEtaSamples = 0;
        private boolean awaitingPostDrop = false;
        private boolean postDropHit = false;
        private boolean resolved = false;
        private long idleTick = -1L;
        private Instant idleAt;
        private GeoPoint idleLocation;
        private double actualPostCompletionEmptyKm = Double.NaN;
        private double actualNextOrderIdleMinutes = Double.NaN;

        private Entry(String traceId,
                      String driverId,
                      String bundleId,
                      CompactPlanType planType,
                      Set<String> orderIds,
                      PlanFeatureVector featureVector,
                      AdaptiveScoreBreakdown scoreBreakdown,
                      WeightSnapshot snapshotBefore,
                      Instant decisionTime,
                      double predictedEtaMinutes,
                      double plannedDeadheadKm,
                      double plannedRevenue,
                      double plannedLandingScore,
                      double predictedPostDropDemandProbability,
                      double plannedEmptyKm,
                      double predictedNextOrderIdleMinutes,
                      double predictedCancelRisk,
                      double predictedOnTimeProbability,
                      double predictedTripDistanceKm) {
            this.traceId = traceId;
            this.driverId = driverId;
            this.bundleId = bundleId;
            this.planType = planType;
            this.orderIds = orderIds;
            this.featureVector = featureVector;
            this.scoreBreakdown = scoreBreakdown;
            this.snapshotBefore = snapshotBefore;
            this.decisionTime = decisionTime;
            this.predictedEtaMinutes = predictedEtaMinutes;
            this.plannedDeadheadKm = plannedDeadheadKm;
            this.plannedRevenue = plannedRevenue;
            this.plannedLandingScore = plannedLandingScore;
            this.predictedPostDropDemandProbability = predictedPostDropDemandProbability;
            this.plannedEmptyKm = plannedEmptyKm;
            this.predictedNextOrderIdleMinutes = predictedNextOrderIdleMinutes;
            this.predictedCancelRisk = predictedCancelRisk;
            this.predictedOnTimeProbability = predictedOnTimeProbability;
            this.predictedTripDistanceKm = predictedTripDistanceKm;
        }

        private boolean isTerminal() {
            return deliveredOrderIds.size() + cancelledOrderIds.size() >= orderIds.size();
        }

        private CompactDecisionResolution toResolution(Instant resolvedAt) {
            int totalOrders = Math.max(1, orderIds.size());
            int delivered = deliveredOrderIds.size();
            int cancelled = cancelledOrderIds.size();
            double completion = delivered / (double) totalOrders;
            double onTime = delivered == 0 ? 0.0 : onTimeDeliveries / (double) delivered;
            double deadheadEfficiency = PlanFeatureVector.clamp01(1.0 - (plannedDeadheadKm / 5.0));
            double profit = PlanFeatureVector.clamp01(
                    (realizedRevenue - plannedDeadheadKm * 2500.0 - cancelled * 3500.0)
                            / Math.max(15000.0, plannedRevenue));
            double landing = PlanFeatureVector.clamp01(postDropHit ? 1.0 : plannedLandingScore * 0.65);
            double postDropQuality = PlanFeatureVector.clamp01(postDropHit
                    ? 1.0
                    : 1.0 - Math.min(1.0, plannedEmptyKm / 3.0));
            double cancelAvoidance = 1.0 - (cancelled / (double) totalOrders);
            double actualEtaMinutes = actualEtaSamples == 0 ? predictedEtaMinutes : actualEtaMinutesSum / actualEtaSamples;
            OutcomeVector outcomeVector = new OutcomeVector(
                    onTime,
                    completion,
                    deadheadEfficiency,
                    profit,
                    landing,
                    postDropQuality,
                    cancelAvoidance);
            DecisionLogRecord decisionLog = new DecisionLogRecord(
                    traceId,
                    driverId,
                    bundleId,
                    planType,
                    List.copyOf(orderIds),
                    scoreBreakdown.regimeKey(),
                    featureVector,
                    scoreBreakdown,
                    snapshotBefore,
                    decisionTime,
                    scoreBreakdown.finalScore(),
                    RewardProjection.project(scoreBreakdown, featureVector),
                    predictedEtaMinutes,
                    plannedDeadheadKm,
                    plannedRevenue,
                    plannedLandingScore,
                    predictedPostDropDemandProbability,
                    plannedEmptyKm,
                    predictedNextOrderIdleMinutes,
                    predictedCancelRisk,
                    predictedOnTimeProbability,
                    predictedTripDistanceKm);
            ResolvedDecisionSample resolvedSample = new ResolvedDecisionSample(
                    decisionLog,
                    outcomeVector,
                    DecisionOutcomeStage.AFTER_POST_DROP_WINDOW,
                    actualEtaMinutes,
                    cancelled > 0,
                    postDropHit,
                    actualPostCompletionEmptyKm,
                    actualNextOrderIdleMinutes,
                    resolvedAt == null ? decisionTime : resolvedAt);
            return new CompactDecisionResolution(
                    traceId,
                    driverId,
                    bundleId,
                    List.copyOf(orderIds),
                    scoreBreakdown.regimeKey(),
                    featureVector,
                    outcomeVector,
                    snapshotBefore,
                    null,
                    scoreBreakdown,
                    decisionLog,
                    resolvedSample,
                    postDropHit,
                    resolvedAt == null ? decisionTime : resolvedAt);
        }
    }

    private static double durationMinutes(Instant from, Instant to) {
        if (from == null || to == null) {
            return Double.NaN;
        }
        return Math.max(0.0, Duration.between(from, to).toSeconds() / 60.0);
    }

    private static double distanceKm(GeoPoint from, GeoPoint to) {
        if (from == null || to == null) {
            return Double.NaN;
        }
        return Math.max(0.0, from.distanceTo(to) / 1000.0);
    }
}
