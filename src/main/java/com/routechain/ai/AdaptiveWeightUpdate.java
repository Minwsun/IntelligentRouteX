package com.routechain.ai;

/**
 * Small bounded delta to adaptive utility weights and their confidence.
 */
public record AdaptiveWeightUpdate(
        double pickupCostDelta,
        double batchSynergyDelta,
        double dropCoherenceDelta,
        double slaRiskDelta,
        double deadheadPenaltyDelta,
        double futureOpportunityDelta,
        double positioningValueDelta,
        double stressPenaltyDelta,
        double pickupCostConfidenceDelta,
        double batchSynergyConfidenceDelta,
        double dropCoherenceConfidenceDelta,
        double slaRiskConfidenceDelta,
        double deadheadPenaltyConfidenceDelta,
        double futureOpportunityConfidenceDelta,
        double positioningValueConfidenceDelta,
        double stressPenaltyConfidenceDelta
) {
    public static AdaptiveWeightUpdate zero() {
        return new AdaptiveWeightUpdate(
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }
}
