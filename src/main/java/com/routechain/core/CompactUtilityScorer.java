package com.routechain.core;

import com.routechain.domain.Order;
import com.routechain.domain.Region;
import com.routechain.simulation.DispatchPlan;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

public class CompactUtilityScorer {

    public PlanFeatureVector extract(DispatchPlan plan, CompactDispatchContext context) {
        return new PlanFeatureVector(
                PlanFeatureVector.clamp01(plan.getOnTimeProbability()),
                normalizeDeadhead(plan.getPredictedDeadheadKm()),
                PlanFeatureVector.clamp01(plan.getBundleEfficiency()),
                computeMerchantAlignment(plan),
                PlanFeatureVector.clamp01(plan.getDeliveryCorridorScore()),
                PlanFeatureVector.clamp01(plan.getLastDropLandingScore()),
                normalizeEmptyKm(plan.getExpectedPostCompletionEmptyKm()),
                PlanFeatureVector.clamp01(plan.getCancellationRisk()));
    }

    public double baseConfidence(DispatchPlan plan) {
        double confidence = plan.getOnTimeProbability() * 0.30
                + plan.getBundleEfficiency() * 0.18
                + plan.getDeliveryCorridorScore() * 0.14
                + plan.getLastDropLandingScore() * 0.14
                + (1.0 - normalizeDeadhead(plan.getPredictedDeadheadKm())) * 0.14
                + (1.0 - normalizeEmptyKm(plan.getExpectedPostCompletionEmptyKm())) * 0.10;
        return PlanFeatureVector.clamp01(confidence);
    }

    public OutcomeVector expectedOutcome(DispatchPlan plan, CompactDispatchContext context) {
        double completion = plan.getOrders().isEmpty() ? 0.0 : 1.0;
        double profit = plan.getOrders().isEmpty()
                ? 0.0
                : PlanFeatureVector.clamp01(plan.getDriverProfit() / (plan.getOrders().size() * 15000.0));
        return new OutcomeVector(
                PlanFeatureVector.clamp01(plan.getOnTimeProbability()),
                completion,
                1.0 - normalizeDeadhead(plan.getPredictedDeadheadKm()),
                profit,
                PlanFeatureVector.clamp01(plan.getLastDropLandingScore()),
                1.0 - normalizeEmptyKm(plan.getExpectedPostCompletionEmptyKm()),
                1.0 - PlanFeatureVector.clamp01(plan.getCancellationRisk()));
    }

    public double regionOpportunityScore(String regionId, List<Region> regions) {
        if (regionId == null || regions == null) {
            return 0.5;
        }
        return regions.stream()
                .filter(region -> regionId.equals(region.getId()))
                .max(Comparator.comparingDouble(Region::getOpportunityScore))
                .map(region -> PlanFeatureVector.clamp01(region.getOpportunityScore()))
                .orElse(0.5);
    }

    private double computeMerchantAlignment(DispatchPlan plan) {
        if (plan.getOrders().isEmpty()) {
            return 0.5;
        }
        double total = 0.0;
        int seen = 0;
        for (Order order : plan.getOrders()) {
            DispatchPlan.Stop pickup = plan.getSequence().stream()
                    .filter(stop -> stop.type() == DispatchPlan.Stop.StopType.PICKUP)
                    .filter(stop -> order.getId().equals(stop.orderId()))
                    .findFirst()
                    .orElse(null);
            if (pickup == null || order.getPredictedReadyAt() == null || order.getCreatedAt() == null) {
                continue;
            }
            double readyMinutes = Duration.between(order.getCreatedAt(), order.getPredictedReadyAt()).toSeconds() / 60.0;
            double delta = Math.abs(readyMinutes - pickup.estimatedArrivalMinutes());
            total += PlanFeatureVector.clamp01(1.0 - delta / 8.0);
            seen++;
        }
        return seen == 0 ? 0.55 : total / seen;
    }

    private double normalizeDeadhead(double deadheadKm) {
        return PlanFeatureVector.clamp01(deadheadKm / 5.0);
    }

    private double normalizeEmptyKm(double emptyKm) {
        return PlanFeatureVector.clamp01(emptyKm / 3.0);
    }
}
