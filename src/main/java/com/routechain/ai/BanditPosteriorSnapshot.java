package com.routechain.ai;

/**
 * Immutable checkpoint for adaptive route utility weights.
 */
public record BanditPosteriorSnapshot(
        double pickupCostWeight,
        double batchSynergyWeight,
        double dropCoherenceWeight,
        double slaRiskWeight,
        double deadheadPenaltyWeight,
        double futureOpportunityWeight,
        double positioningValueWeight,
        double stressPenaltyWeight,
        double pickupCostConfidence,
        double batchSynergyConfidence,
        double dropCoherenceConfidence,
        double slaRiskConfidence,
        double deadheadPenaltyConfidence,
        double futureOpportunityConfidence,
        double positioningValueConfidence,
        double stressPenaltyConfidence,
        long updateCount,
        long checkpointVersion
) {}
