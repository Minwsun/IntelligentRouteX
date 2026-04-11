package com.routechain.core;

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
                               List<String> orderIds,
                               PlanFeatureVector featureVector,
                               AdaptiveScoreBreakdown scoreBreakdown,
                               WeightSnapshot snapshotBefore,
                               Instant decisionTime,
                               double plannedDeadheadKm,
                               double plannedRevenue,
                               double plannedLandingScore,
                               double plannedEmptyKm) {
        Entry entry = new Entry(
                traceId,
                driverId,
                bundleId,
                new LinkedHashSet<>(orderIds),
                featureVector,
                scoreBreakdown,
                snapshotBefore,
                decisionTime,
                plannedDeadheadKm,
                plannedRevenue,
                plannedLandingScore,
                plannedEmptyKm);
        entriesByTrace.put(traceId, entry);
        traceByDriver.put(driverId, traceId);
    }

    public void recordOrderDelivered(String traceId, String orderId, boolean onTime, double fee) {
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

    public void markDriverIdle(String driverId, long tick, Instant when) {
        Entry entry = entryForDriver(driverId);
        if (entry == null || entry.resolved) {
            return;
        }
        if (entry.isTerminal()) {
            entry.awaitingPostDrop = true;
            entry.idleTick = tick;
            entry.idleAt = when;
        }
    }

    public CompactDecisionResolution recordPostDropHit(String driverId,
                                                       long tick,
                                                       Instant when) {
        Entry entry = entryForDriver(driverId);
        if (entry == null || entry.resolved || !entry.awaitingPostDrop) {
            return null;
        }
        entry.postDropHit = true;
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
        private final Set<String> orderIds;
        private final PlanFeatureVector featureVector;
        private final AdaptiveScoreBreakdown scoreBreakdown;
        private final WeightSnapshot snapshotBefore;
        private final Instant decisionTime;
        private final double plannedDeadheadKm;
        private final double plannedRevenue;
        private final double plannedLandingScore;
        private final double plannedEmptyKm;
        private final Set<String> deliveredOrderIds = new LinkedHashSet<>();
        private final Set<String> cancelledOrderIds = new LinkedHashSet<>();
        private double realizedRevenue = 0.0;
        private int onTimeDeliveries = 0;
        private boolean awaitingPostDrop = false;
        private boolean postDropHit = false;
        private boolean resolved = false;
        private long idleTick = -1L;
        private Instant idleAt;

        private Entry(String traceId,
                      String driverId,
                      String bundleId,
                      Set<String> orderIds,
                      PlanFeatureVector featureVector,
                      AdaptiveScoreBreakdown scoreBreakdown,
                      WeightSnapshot snapshotBefore,
                      Instant decisionTime,
                      double plannedDeadheadKm,
                      double plannedRevenue,
                      double plannedLandingScore,
                      double plannedEmptyKm) {
            this.traceId = traceId;
            this.driverId = driverId;
            this.bundleId = bundleId;
            this.orderIds = orderIds;
            this.featureVector = featureVector;
            this.scoreBreakdown = scoreBreakdown;
            this.snapshotBefore = snapshotBefore;
            this.decisionTime = decisionTime;
            this.plannedDeadheadKm = plannedDeadheadKm;
            this.plannedRevenue = plannedRevenue;
            this.plannedLandingScore = plannedLandingScore;
            this.plannedEmptyKm = plannedEmptyKm;
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
            OutcomeVector outcomeVector = new OutcomeVector(
                    onTime,
                    completion,
                    deadheadEfficiency,
                    profit,
                    landing,
                    postDropQuality,
                    cancelAvoidance);
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
                    postDropHit,
                    resolvedAt == null ? decisionTime : resolvedAt);
        }
    }
}
