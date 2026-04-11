package com.routechain.ai;

import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.simulation.DispatchPlan;

/**
 * Projects a rich dispatch plan into a graph-aware utility state.
 */
public final class GraphFeatureProjector {

    public GraphRouteState project(DispatchPlan plan,
                                   DriverDecisionContext ctx,
                                   SpatiotemporalField field,
                                   int estimatedEndHour,
                                   WeatherProfile weather,
                                   StressRegime stressRegime) {
        String zoneKey = field == null || plan == null || plan.getEndZonePoint() == null
                ? "zone-unknown"
                : field.cellKeyOf(plan.getEndZonePoint());
        double pickupCost = clamp01(
                plan.getPredictedDeadheadKm() / 4.0
                        + plan.getPickupFrictionScore() * 0.55
                        + plan.getPickupSpreadKm() / 4.0 * 0.25);
        double batchSynergy = clamp01(
                plan.getBundleEfficiency() * 0.46
                        + plan.getDeliveryCorridorScore() * 0.18
                        + Math.max(0.0, 1.0 - plan.getMarginalDeadheadPerAddedOrder() / 2.5) * 0.18
                        + Math.max(0.0, 1.0 - plan.getPickupSpreadKm() / 2.0) * 0.18);
        double dropCoherence = clamp01(
                plan.getLastDropLandingScore() * 0.45
                        + plan.getDropReachabilityScore() * 0.27
                        + Math.max(0.0, 1.0 - plan.getDeliveryZigZagPenalty()) * 0.28);
        double slaRisk = clamp01(plan.getLateRisk() * 0.72 + plan.getCancellationRisk() * 0.28);
        double deadheadPenalty = clamp01(
                plan.getPredictedDeadheadKm() / 4.0 * 0.72
                        + plan.getExpectedPostCompletionEmptyKm() / 3.0 * 0.28);
        double futureOpportunity = clamp01(
                plan.getPostDropDemandProbability() * 0.42
                        + plan.getContinuationValueScore() * 0.24
                        + plan.getEndZoneOpportunityScore() * 0.18
                        + Math.max(0.0, 1.0 - plan.getExpectedNextOrderIdleMinutes() / 8.0) * 0.16);
        double positioningValue = clamp01(
                plan.getPositioningValueScore() * 0.42
                        + plan.getGraphAffinityScore() * 0.20
                        + plan.getDropReachabilityScore() * 0.18
                        + Math.max(0.0, 1.0 - plan.getExpectedPostCompletionEmptyKm() / 3.0) * 0.20);
        double stressSeverity = switch (stressRegime == null ? StressRegime.NORMAL : stressRegime) {
            case NORMAL -> 0.10;
            case STRESS -> 0.55;
            case SEVERE_STRESS -> 0.90;
        };
        double stressPenalty = clamp01(
                stressSeverity * 0.44
                        + plan.getTrafficExposureScore() * 0.20
                        + plan.getWeatherExposureScore() * 0.18
                        + plan.getTrafficUncertaintyScore() * 0.10
                        + plan.getBorrowedDependencyScore() * 0.08);
        if (ctx != null) {
            futureOpportunity = clamp01(futureOpportunity * 0.82 + clamp01(ctx.localPostDropOpportunity()) * 0.18);
            stressPenalty = clamp01(stressPenalty * 0.82 + clamp01(ctx.localCorridorExposure()) * 0.18);
        }
        return new GraphRouteState(
                zoneKey,
                plan.getServiceTier(),
                plan.getSelectionBucket(),
                weather,
                ((estimatedEndHour % 24) + 24) % 24,
                plan.getBundleSize(),
                pickupCost,
                batchSynergy,
                dropCoherence,
                slaRisk,
                deadheadPenalty,
                futureOpportunity,
                positioningValue,
                stressPenalty,
                clamp01(plan.getGraphAffinityScore()),
                clamp01(plan.getPostDropDemandProbability()),
                Math.max(0.0, plan.getExpectedNextOrderIdleMinutes()),
                Math.max(0.0, plan.getExpectedPostCompletionEmptyKm()),
                clamp01(plan.getBorrowedDependencyScore()),
                clamp01(plan.getEmptyRiskAfter()));
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
