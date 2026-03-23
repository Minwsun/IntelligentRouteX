package com.routechain.ai;

import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan;
import com.routechain.simulation.DispatchPlan.Stop;
import com.routechain.simulation.DispatchPlan.Stop.StopType;

import java.util.List;

/**
 * Centralized 12-term objective function for scoring dispatch plans.
 *
 * Replaces inline scoring in OmegaDispatchAgent with a clean, testable scorer.
 *
 * Utility =
 *   + w1 * OnTimeScore
 *   + w2 * DriverProfitScore
 *   + w3 * PickupWaveEfficiency
 *   + w4 * DropSequenceEfficiency
 *   + w5 * EndStateOpportunity
 *   + w6 * NextOrderAcquisitionScore
 *   - p1 * DeadheadPenalty
 *   - p2 * MerchantWaitPenalty
 *   - p3 * CongestionPenalty
 *   - p4 * LateRiskPenalty
 *   - p5 * CancellationRiskPenalty
 *   - p6 * OverloadPenalty
 */
public class PlanUtilityScorer {

    // ── Positive weights ────────────────────────────────────────────────
    private static final double W_ON_TIME = 0.20;
    private static final double W_PROFIT = 0.15;
    private static final double W_PICKUP_WAVE = 0.10;
    private static final double W_DROP_EFFICIENCY = 0.10;
    private static final double W_END_STATE = 0.08;
    private static final double W_NEXT_ORDER = 0.07;

    // ── Penalty weights ─────────────────────────────────────────────────
    private static final double P_DEADHEAD = 0.10;
    private static final double P_MERCHANT_WAIT = 0.05;
    private static final double P_CONGESTION = 0.05;
    private static final double P_LATE_RISK = 0.05;
    private static final double P_CANCEL_RISK = 0.03;
    private static final double P_OVERLOAD = 0.02;

    // ── Normalization thresholds ─────────────────────────────────────────
    private static final double PROFIT_NORM = 50000.0;  // VND
    private static final double DEADHEAD_THRESHOLD_KM = 6.0;
    private static final double MERCHANT_WAIT_THRESHOLD_MIN = 8.0;

    /**
     * Compute the utility score for a fully-predicted dispatch plan.
     * All prediction fields (lateRisk, driverProfit, etc.) must be set before calling.
     *
     * @param plan the scored plan with all predictions filled
     * @return utility score (higher = better plan)
     */
    public double score(DispatchPlan plan) {
        double utility = 0;

        // ── Positive terms ──────────────────────────────────────────────

        // 1. On-time probability
        utility += W_ON_TIME * plan.getOnTimeProbability();

        // 2. Driver profit (normalized)
        double profitScore = Math.max(0, Math.min(1.0,
                plan.getDriverProfit() / PROFIT_NORM));
        utility += W_PROFIT * profitScore;

        // 3. Pickup-wave efficiency — ratio of pickups completed before first drop
        double pickupWaveEff = computePickupWaveEfficiency(plan.getSequence());
        utility += W_PICKUP_WAVE * pickupWaveEff;

        // 4. Drop sequence efficiency (bundle efficiency)
        utility += W_DROP_EFFICIENCY * Math.min(1.0, plan.getBundleEfficiency());

        // 5. End-state opportunity (continuation value)
        utility += W_END_STATE * Math.min(1.0, plan.getEndZoneOpportunity());

        // 6. Next order acquisition
        utility += W_NEXT_ORDER * Math.min(1.0, plan.getNextOrderAcquisitionScore());

        // ── Penalty terms ───────────────────────────────────────────────

        // 7. Deadhead penalty
        double deadheadNorm = Math.min(1.0,
                plan.getPredictedDeadheadKm() / DEADHEAD_THRESHOLD_KM);
        utility -= P_DEADHEAD * deadheadNorm;

        // 8. Merchant wait penalty
        double merchantWait = computeMerchantWaitScore(plan);
        utility -= P_MERCHANT_WAIT * merchantWait;

        // 9. Congestion penalty
        utility -= P_CONGESTION * Math.min(1.0, plan.getCongestionPenalty());

        // 10. Late risk penalty (quadratic — severe late risk is much worse)
        double lateRiskSq = plan.getLateRisk() * plan.getLateRisk();
        utility -= P_LATE_RISK * lateRiskSq;

        // 11. Cancellation risk penalty
        utility -= P_CANCEL_RISK * Math.min(1.0, plan.getCancellationRisk());

        // 12. Overload penalty (more orders = more risk)
        int bundleSize = plan.getBundleSize();
        double overloadPenalty = bundleSize > 3 ? (bundleSize - 3) * 0.3 : 0;
        utility -= P_OVERLOAD * Math.min(1.0, overloadPenalty);

        return Math.max(0.001, utility);
    }

    /**
     * Pickup-wave efficiency: how many pickups happen before the first dropoff.
     * Perfect wave (all pickups first) = 1.0
     * Interleaved (P,D,P,D) = 0.5
     * Single order = 1.0
     */
    private double computePickupWaveEfficiency(List<Stop> sequence) {
        if (sequence.isEmpty()) return 0;

        int totalPickups = 0;
        int pickupsBeforeFirstDrop = 0;
        boolean firstDropSeen = false;

        for (Stop stop : sequence) {
            if (stop.type() == StopType.PICKUP) {
                totalPickups++;
                if (!firstDropSeen) pickupsBeforeFirstDrop++;
            } else if (stop.type() == StopType.DROPOFF) {
                firstDropSeen = true;
            }
        }

        return totalPickups > 0 ? (double) pickupsBeforeFirstDrop / totalPickups : 1.0;
    }

    /**
     * Merchant wait score: sum of predicted pickup wait / threshold.
     * Returns a normalized 0..1 value.
     */
    private double computeMerchantWaitScore(DispatchPlan plan) {
        if (plan.getOrders().isEmpty()) return 0;

        double totalWaitMin = 0;
        for (Order order : plan.getOrders()) {
            if (order.getPredictedReadyAt() != null && order.getCreatedAt() != null) {
                long prepTimeMs = java.time.Duration.between(
                        order.getCreatedAt(), order.getPredictedReadyAt()).toMillis();
                double prepMin = prepTimeMs / 60000.0;
                // Upper bound heuristic: if prep > 10min, it's a significant wait
                if (prepMin > 3.0) {
                    totalWaitMin += Math.min(prepMin, 15.0);
                }
            }
        }

        return Math.min(1.0, totalWaitMin / (MERCHANT_WAIT_THRESHOLD_MIN * plan.getOrders().size()));
    }
}
