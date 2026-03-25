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
        return validate(plan, dynamicBatchCap, rejectReasons, StressRegime.NORMAL);
    }

    public boolean validate(DispatchPlan plan,
                            int dynamicBatchCap,
                            int[] rejectReasons,
                            StressRegime stressRegime) {
        if (!isLateRiskAcceptable(plan, stressRegime)) {
            rejectReasons[0]++;
            return false;
        }
        if (!isProfitFloorMet(plan, stressRegime)) {
            rejectReasons[1]++;
            return false;
        }
        if (!isDeadheadAcceptable(plan, stressRegime)) {
            rejectReasons[2]++;
            return false;
        }
        if (!isDetourAcceptable(plan, stressRegime)) {
            rejectReasons[3]++;
            return false;
        }
        if (!isMerchantWaitAcceptable(plan, stressRegime)) {
            rejectReasons[4]++;
            return false;
        }
        if (!isDriverLoadAcceptable(plan, dynamicBatchCap, stressRegime)) {
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

    public boolean isLateRiskAcceptable(DispatchPlan plan, StressRegime stressRegime) {
        double limit = switch (stressRegime) {
            case NORMAL -> MAX_LATE_RISK;
            case STRESS -> 0.32;
            case SEVERE_STRESS -> 0.26;
        };
        if (plan.isWaveLaunchEligible() && plan.getBundleSize() >= 3 && stressRegime != StressRegime.SEVERE_STRESS) {
            limit += 0.08;
        }
        return plan.getLateRisk() <= limit;
    }

    /**
     * Profit per order must be at least 3000 VND.
     * Plans with no orders (hold/reposition) are exempt.
     */
    public boolean isProfitFloorMet(DispatchPlan plan, StressRegime stressRegime) {
        List<Order> orders = plan.getOrders();
        if (orders.isEmpty()) return true;
        double profitPerOrder = plan.getDriverProfit() / orders.size();
        double floor = switch (stressRegime) {
            case NORMAL -> MIN_PROFIT_PER_ORDER_VND;
            case STRESS -> 3000.0;
            case SEVERE_STRESS -> 3400.0;
        };
        return profitPerOrder >= floor;
    }

    public boolean isProfitFloorMet(DispatchPlan plan) {
        return isProfitFloorMet(plan, StressRegime.NORMAL);
    }

    /**
     * Deadhead distance must not exceed 6km.
     */
    public boolean isDeadheadAcceptable(DispatchPlan plan, StressRegime stressRegime) {
        double limit = switch (stressRegime) {
            case NORMAL -> MAX_DEADHEAD_KM;
            case STRESS -> 4.6;
            case SEVERE_STRESS -> 2.9;
        };
        if (plan.isWaveLaunchEligible() && plan.getBundleSize() >= 3 && stressRegime != StressRegime.SEVERE_STRESS) {
            limit += 0.6;
        }
        if (plan.getCongestionPenalty() > 0.90) {
            limit = Math.min(limit, 2.9);
        } else if (plan.getCongestionPenalty() > 0.78) {
            limit = Math.min(limit, 3.9);
        }
        return plan.getPredictedDeadheadKm() <= limit;
    }

    public boolean isDeadheadAcceptable(DispatchPlan plan) {
        return isDeadheadAcceptable(plan, StressRegime.NORMAL);
    }

    /**
     * Detour ratio must not exceed 2.0.
     * Detour ratio = total route distance / sum of standalone order distances.
     */
    public boolean isDetourAcceptable(DispatchPlan plan, StressRegime stressRegime) {
        List<Order> orders = plan.getOrders();
        if (orders.isEmpty()) return true;

        double standaloneDistKm = orders.stream()
                .mapToDouble(o -> o.getPickupPoint()
                        .distanceTo(o.getDropoffPoint()) / 1000.0)
                .sum();
        if (standaloneDistKm <= 0) return true;

        double totalDistKm = computeRouteDistanceKm(plan);
        double detourRatio = totalDistKm / standaloneDistKm;
        double limit = switch (stressRegime) {
            case NORMAL -> MAX_DETOUR_RATIO;
            case STRESS -> 2.5;
            case SEVERE_STRESS -> 2.0;
        };
        if (plan.isWaveLaunchEligible() && plan.getBundleSize() >= 3 && stressRegime != StressRegime.SEVERE_STRESS) {
            limit += 0.2;
        }
        return detourRatio <= limit;
    }

    public boolean isDetourAcceptable(DispatchPlan plan) {
        return isDetourAcceptable(plan, StressRegime.NORMAL);
    }

    /**
     * Cumulative merchant wait across all pickup stops must not exceed 8 minutes.
     */
    public boolean isMerchantWaitAcceptable(DispatchPlan plan, StressRegime stressRegime) {
        double maxMerchantWait = switch (stressRegime) {
            case NORMAL -> MAX_CUMULATIVE_MERCHANT_WAIT_MIN;
            case STRESS -> 9.0;
            case SEVERE_STRESS -> 6.5;
        };
        if (plan.isWaveLaunchEligible() && plan.getBundleSize() >= 3 && stressRegime != StressRegime.SEVERE_STRESS) {
            maxMerchantWait += 1.5;
        }
        if (plan.getCongestionPenalty() > 0.90) {
            maxMerchantWait = Math.min(maxMerchantWait, 7.0);
        } else if (plan.getCongestionPenalty() > 0.78) {
            maxMerchantWait = Math.min(maxMerchantWait, 9.0);
        }
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
        return cumulativeWait <= maxMerchantWait;
    }

    public boolean isMerchantWaitAcceptable(DispatchPlan plan) {
        return isMerchantWaitAcceptable(plan, StressRegime.NORMAL);
    }

    /**
     * Bundle size must not exceed the dynamic batch cap for this driver context.
     */
    public boolean isDriverLoadAcceptable(DispatchPlan plan,
                                          int dynamicBatchCap,
                                          StressRegime stressRegime) {
        int hardCap = switch (stressRegime) {
            case NORMAL -> dynamicBatchCap;
            case STRESS -> Math.min(dynamicBatchCap, 3);
            case SEVERE_STRESS -> Math.min(dynamicBatchCap, 2);
        };
        return plan.getBundleSize() <= hardCap;
    }

    public boolean isDriverLoadAcceptable(DispatchPlan plan, int dynamicBatchCap) {
        return isDriverLoadAcceptable(plan, dynamicBatchCap, StressRegime.NORMAL);
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
