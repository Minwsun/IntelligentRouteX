package com.routechain.ai;

import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan;
import com.routechain.simulation.DispatchPlan.Stop;
import com.routechain.simulation.DispatchPlan.Stop.StopType;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Centralized utility scorer for dispatch plans.
 *
 * The scorer mixes short-term execution quality with longer-term landing value.
 * It favors compact pickup waves, readiness-aligned pickup timing, and plans
 * that finish in zones with strong next-order opportunity.
 */
public class PlanUtilityScorer {

    private static final double W_ON_TIME = 0.26;
    private static final double W_PROFIT = 0.09;
    private static final double W_PICKUP_WAVE = 0.12;
    private static final double W_PICKUP_COMPACTNESS = 0.10;
    private static final double W_MERCHANT_ALIGNMENT = 0.08;
    private static final double W_DROP_EFFICIENCY = 0.14;
    private static final double W_FIRST_ORDER_PROTECTION = 0.08;
    private static final double W_END_STATE = 0.07;
    private static final double W_NEXT_ORDER = 0.06;
    private static final double W_FUTURE_ZONE_LANDING = 0.08;

    private static final double P_DEADHEAD = 0.24;
    private static final double P_MERCHANT_WAIT = 0.06;
    private static final double P_CONGESTION = 0.08;
    private static final double P_LATE_RISK = 0.24;
    private static final double P_CANCEL_RISK = 0.14;
    private static final double P_OVERLOAD = 0.03;

    private static final double PROFIT_NORM = 50000.0;
    private static final double DEADHEAD_THRESHOLD_KM = 5.0;
    private static final double MERCHANT_WAIT_THRESHOLD_MIN = 8.0;

    public double score(DispatchPlan plan) {
        double utility = 0.0;

        utility += W_ON_TIME * clamp01(plan.getOnTimeProbability());
        utility += W_PROFIT * clamp01(plan.getDriverProfit() / PROFIT_NORM);
        utility += W_PICKUP_WAVE * computePickupWaveEfficiency(plan.getSequence());
        utility += W_PICKUP_COMPACTNESS * computePickupWaveCompactness(plan.getSequence());
        utility += W_MERCHANT_ALIGNMENT * computeMerchantReadinessAlignment(plan);
        utility += W_DROP_EFFICIENCY * clamp01(plan.getBundleEfficiency());
        utility += W_FIRST_ORDER_PROTECTION * computeFirstOrderProtection(plan);
        utility += W_END_STATE * clamp01(plan.getEndZoneOpportunity());
        utility += W_NEXT_ORDER * clamp01(plan.getNextOrderAcquisitionScore());
        utility += W_FUTURE_ZONE_LANDING * computeFutureZoneLandingScore(plan);

        utility -= P_DEADHEAD * clamp01(plan.getPredictedDeadheadKm() / DEADHEAD_THRESHOLD_KM);
        utility -= P_MERCHANT_WAIT * computeMerchantWaitPenalty(plan);
        utility -= P_CONGESTION * clamp01(plan.getCongestionPenalty());
        utility -= P_LATE_RISK * square(clamp01(plan.getLateRisk()));
        utility -= P_CANCEL_RISK * clamp01(plan.getCancellationRisk());
        utility -= P_OVERLOAD * computeOverloadPenalty(plan.getBundleSize());

        return Math.max(0.001, utility);
    }

    private double computePickupWaveEfficiency(List<Stop> sequence) {
        if (sequence.isEmpty()) {
            return 0.0;
        }

        int totalPickups = 0;
        int pickupsBeforeFirstDrop = 0;
        boolean firstDropSeen = false;

        for (Stop stop : sequence) {
            if (stop.type() == StopType.PICKUP) {
                totalPickups++;
                if (!firstDropSeen) {
                    pickupsBeforeFirstDrop++;
                }
            } else if (stop.type() == StopType.DROPOFF) {
                firstDropSeen = true;
            }
        }

        return totalPickups > 0 ? (double) pickupsBeforeFirstDrop / totalPickups : 1.0;
    }

    private double computePickupWaveCompactness(List<Stop> sequence) {
        List<Stop> pickups = sequence.stream()
                .filter(stop -> stop.type() == StopType.PICKUP)
                .toList();
        if (pickups.size() <= 1) {
            return 1.0;
        }

        double totalLegKm = 0.0;
        int legs = 0;
        for (int i = 1; i < pickups.size(); i++) {
            totalLegKm += pickups.get(i - 1).location().distanceTo(pickups.get(i).location()) / 1000.0;
            legs++;
        }
        double averageLegKm = legs == 0 ? 0.0 : totalLegKm / legs;
        return clamp01(1.0 / (1.0 + averageLegKm / 1.2));
    }

    private double computeMerchantReadinessAlignment(DispatchPlan plan) {
        if (plan.getOrders().isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        int count = 0;
        for (Order order : plan.getOrders()) {
            Stop pickupStop = findStop(plan.getSequence(), order.getId(), StopType.PICKUP);
            if (pickupStop == null || order.getPredictedReadyAt() == null || order.getCreatedAt() == null) {
                continue;
            }

            double predictedReadyMinutes = Duration.between(
                    order.getCreatedAt(), order.getPredictedReadyAt()).toSeconds() / 60.0;
            double arrivalMinutes = pickupStop.estimatedArrivalMinutes();
            double diffMinutes = Math.abs(arrivalMinutes - predictedReadyMinutes);
            total += clamp01(1.0 - diffMinutes / 8.0);
            count++;
        }

        return count == 0 ? 0.5 : total / count;
    }

    private double computeFirstOrderProtection(DispatchPlan plan) {
        if (plan.getOrders().isEmpty()) {
            return 0.0;
        }

        Order earliest = plan.getOrders().stream()
                .min(Comparator.comparing(Order::getCreatedAt))
                .orElse(null);
        if (earliest == null) {
            return 0.0;
        }

        Stop firstDrop = findStop(plan.getSequence(), earliest.getId(), StopType.DROPOFF);
        if (firstDrop == null) {
            return 0.0;
        }

        double arrivalMinutes = firstDrop.estimatedArrivalMinutes();
        double softTarget = Math.max(8.0, earliest.getPromisedEtaMinutes() * 0.75);
        double overflow = Math.max(0.0, arrivalMinutes - softTarget);
        return clamp01(1.0 - overflow / Math.max(10.0, earliest.getPromisedEtaMinutes() * 0.5));
    }

    private double computeFutureZoneLandingScore(DispatchPlan plan) {
        double landingValue = clamp01(plan.getEndZoneOpportunity()) * 0.55
                + clamp01(plan.getNextOrderAcquisitionScore()) * 0.45;
        double riskPenalty = clamp01(plan.getCongestionPenalty()) * 0.50
                + clamp01(plan.getCancellationRisk()) * 0.15;
        return clamp01(landingValue * (1.0 - riskPenalty));
    }

    private double computeMerchantWaitPenalty(DispatchPlan plan) {
        if (plan.getOrders().isEmpty()) {
            return 0.0;
        }

        double totalWaitMinutes = 0.0;
        for (Order order : plan.getOrders()) {
            Stop pickupStop = findStop(plan.getSequence(), order.getId(), StopType.PICKUP);
            if (pickupStop == null || order.getPredictedReadyAt() == null || order.getCreatedAt() == null) {
                continue;
            }

            double predictedReadyMinutes = Duration.between(
                    order.getCreatedAt(), order.getPredictedReadyAt()).toSeconds() / 60.0;
            double waitMinutes = Math.max(0.0, predictedReadyMinutes - pickupStop.estimatedArrivalMinutes());
            totalWaitMinutes += Math.min(waitMinutes, 15.0);
        }

        return clamp01(totalWaitMinutes / (MERCHANT_WAIT_THRESHOLD_MIN * Math.max(1, plan.getOrders().size())));
    }

    private double computeOverloadPenalty(int bundleSize) {
        double overloadPenalty = bundleSize > 3 ? (bundleSize - 3) * 0.3 : 0.0;
        return clamp01(overloadPenalty);
    }

    private Stop findStop(List<Stop> sequence, String orderId, StopType type) {
        for (Stop stop : sequence) {
            if (stop.type() == type && stop.orderId().equals(orderId)) {
                return stop;
            }
        }
        return null;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double square(double value) {
        return value * value;
    }
}
