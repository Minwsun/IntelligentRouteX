package com.routechain.core;

import com.routechain.simulation.DispatchPlan;

public class CompactConstraintGate {
    private static final double MIN_ON_TIME_FLOOR = 0.52;
    private static final double MAX_DEADHEAD_KM = 5.0;
    private static final double MIN_PROFIT_PER_ORDER = 3000.0;
    private static final double MAX_DETOUR_RATIO = 2.35;
    private static final double MAX_MERCHANT_WAIT_MINUTES = 9.0;

    public boolean allow(DispatchPlan plan) {
        if (plan.getOnTimeProbability() < MIN_ON_TIME_FLOOR) {
            return false;
        }
        if (plan.getPredictedDeadheadKm() > MAX_DEADHEAD_KM) {
            return false;
        }
        if (!plan.getOrders().isEmpty()) {
            double profitPerOrder = plan.getDriverProfit() / plan.getOrders().size();
            if (profitPerOrder < MIN_PROFIT_PER_ORDER) {
                return false;
            }
            double standaloneKm = plan.getOrders().stream()
                    .mapToDouble(order -> order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0)
                    .sum();
            if (standaloneKm > 0.0) {
                double routeKm = plan.getPredictedDeadheadKm();
                var sequence = plan.getSequence();
                for (int i = 1; i < sequence.size(); i++) {
                    routeKm += sequence.get(i - 1).location().distanceTo(sequence.get(i).location()) / 1000.0;
                }
                if ((routeKm / standaloneKm) > MAX_DETOUR_RATIO) {
                    return false;
                }
            }
            double merchantWait = Math.max(0.0, plan.getPredictedTotalMinutes() * (1.0 - plan.getBundleEfficiency()) * 0.18);
            if (merchantWait > MAX_MERCHANT_WAIT_MINUTES) {
                return false;
            }
        }
        return true;
    }
}
