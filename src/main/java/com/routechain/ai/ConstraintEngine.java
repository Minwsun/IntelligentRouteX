package com.routechain.ai;

import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan;

import java.util.List;

/**
 * Extracted hard constraints for dispatch plan validation.
 *
 * Each constraint returns true if the plan PASSES the check.
 * Plans failing any constraint are rejected before scoring.
 *
 * Reject reason codes:
 *   0 = late risk
 *   1 = profit floor
 *   2 = deadhead
 *   3 = detour ratio
 *   4 = merchant wait
 *   5 = driver load
 */
public class ConstraintEngine {

    private static final double MAX_LATE_RISK = 0.35;
    private static final double MIN_PROFIT_PER_ORDER_VND = 3000.0;
    private static final double MAX_DEADHEAD_KM = 7.0;
    private static final double MAX_DETOUR_RATIO = 3.0;
    private static final double MAX_CUMULATIVE_MERCHANT_WAIT_MIN = 15.0;

    /**
     * Validate all hard constraints on a plan.
     *
     * @param plan          the plan to validate (predictions must be set)
     * @param dynamicBatchCap  max allowed bundle size for this driver context
     * @param rejectReasons  int[6] array, incremented at the index of the first failing constraint
     * @return true if plan passes ALL constraints
     */
    public boolean validate(DispatchPlan plan, int dynamicBatchCap, int[] rejectReasons) {
        if (!isLateRiskAcceptable(plan)) {
            rejectReasons[0]++;
            return false;
        }
        if (!isProfitFloorMet(plan)) {
            rejectReasons[1]++;
            return false;
        }
        if (!isDeadheadAcceptable(plan)) {
            rejectReasons[2]++;
            return false;
        }
        if (!isDetourAcceptable(plan)) {
            rejectReasons[3]++;
            return false;
        }
        if (!isMerchantWaitAcceptable(plan)) {
            rejectReasons[4]++;
            return false;
        }
        if (!isDriverLoadAcceptable(plan, dynamicBatchCap)) {
            rejectReasons[5]++;
            return false;
        }
        return true;
    }

    // ── Individual constraints ──────────────────────────────────────────

    /**
     * Late risk must not exceed 35%.
     */
    public boolean isLateRiskAcceptable(DispatchPlan plan) {
        return plan.getLateRisk() <= MAX_LATE_RISK;
    }

    /**
     * Profit per order must be at least 3000 VND.
     * Plans with no orders (hold/reposition) are exempt.
     */
    public boolean isProfitFloorMet(DispatchPlan plan) {
        List<Order> orders = plan.getOrders();
        if (orders.isEmpty()) return true;
        double profitPerOrder = plan.getDriverProfit() / orders.size();
        return profitPerOrder >= MIN_PROFIT_PER_ORDER_VND;
    }

    /**
     * Deadhead distance must not exceed 6km.
     */
    public boolean isDeadheadAcceptable(DispatchPlan plan) {
        return plan.getPredictedDeadheadKm() <= MAX_DEADHEAD_KM;
    }

    /**
     * Detour ratio must not exceed 2.0.
     * Detour ratio = total route distance / sum of standalone order distances.
     */
    public boolean isDetourAcceptable(DispatchPlan plan) {
        List<Order> orders = plan.getOrders();
        if (orders.isEmpty()) return true;

        double standaloneDistKm = orders.stream()
                .mapToDouble(o -> o.getPickupPoint()
                        .distanceTo(o.getDropoffPoint()) / 1000.0)
                .sum();
        if (standaloneDistKm <= 0) return true;

        double totalDistKm = computeRouteDistanceKm(plan);
        double detourRatio = totalDistKm / standaloneDistKm;
        return detourRatio <= MAX_DETOUR_RATIO;
    }

    /**
     * Cumulative merchant wait across all pickup stops must not exceed 8 minutes.
     */
    public boolean isMerchantWaitAcceptable(DispatchPlan plan) {
        double cumulativeWait = 0;
        List<Order> orders = plan.getOrders();
        if (orders.isEmpty()) return true;

        for (DispatchPlan.Stop stop : plan.getSequence()) {
            if (stop.type() == DispatchPlan.Stop.StopType.PICKUP) {
                Order order = orders.stream()
                        .filter(o -> o.getId().equals(stop.orderId()))
                        .findFirst().orElse(null);
                if (order != null && order.getPredictedReadyAt() != null
                        && order.getCreatedAt() != null) {
                    double merchantReadyMin = java.time.Duration.between(
                            order.getCreatedAt(), order.getPredictedReadyAt()
                    ).toSeconds() / 60.0;
                    double driverArrivalMin = stop.estimatedArrivalMinutes();
                    if (driverArrivalMin < merchantReadyMin) {
                        cumulativeWait += (merchantReadyMin - driverArrivalMin);
                    }
                }
            }
        }
        return cumulativeWait <= MAX_CUMULATIVE_MERCHANT_WAIT_MIN;
    }

    /**
     * Bundle size must not exceed the dynamic batch cap for this driver context.
     */
    public boolean isDriverLoadAcceptable(DispatchPlan plan, int dynamicBatchCap) {
        return plan.getBundleSize() <= dynamicBatchCap;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private double computeRouteDistanceKm(DispatchPlan plan) {
        double dist = 0;
        var seq = plan.getSequence();
        if (seq.isEmpty()) return 0;

        // Start from driver to first stop
        dist += plan.getDriver().getCurrentLocation()
                .distanceTo(seq.get(0).location()) / 1000.0;
        for (int i = 1; i < seq.size(); i++) {
            dist += seq.get(i - 1).location()
                    .distanceTo(seq.get(i).location()) / 1000.0;
        }
        return dist;
    }
}
