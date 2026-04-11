package com.routechain.ai;

/**
 * Online-updated weight vector for route utility decomposition.
 * We keep the model transparent and bounded so it can adapt safely without retraining.
 */
public final class AdaptiveUtilityWeights {
    private static final double MIN_WEIGHT = -1.25;
    private static final double MAX_WEIGHT = 1.25;
    private static final double MIN_CONFIDENCE = 0.15;
    private static final double MAX_CONFIDENCE = 0.98;

    private double pickupCostWeight = -0.20;
    private double batchSynergyWeight = 0.19;
    private double dropCoherenceWeight = 0.13;
    private double slaRiskWeight = -0.19;
    private double deadheadPenaltyWeight = -0.17;
    private double futureOpportunityWeight = 0.16;
    private double positioningValueWeight = 0.10;
    private double stressPenaltyWeight = -0.09;

    private double pickupCostConfidence = 0.55;
    private double batchSynergyConfidence = 0.55;
    private double dropCoherenceConfidence = 0.50;
    private double slaRiskConfidence = 0.60;
    private double deadheadPenaltyConfidence = 0.58;
    private double futureOpportunityConfidence = 0.50;
    private double positioningValueConfidence = 0.46;
    private double stressPenaltyConfidence = 0.45;

    public void reset() {
        pickupCostWeight = -0.20;
        batchSynergyWeight = 0.19;
        dropCoherenceWeight = 0.13;
        slaRiskWeight = -0.19;
        deadheadPenaltyWeight = -0.17;
        futureOpportunityWeight = 0.16;
        positioningValueWeight = 0.10;
        stressPenaltyWeight = -0.09;

        pickupCostConfidence = 0.55;
        batchSynergyConfidence = 0.55;
        dropCoherenceConfidence = 0.50;
        slaRiskConfidence = 0.60;
        deadheadPenaltyConfidence = 0.58;
        futureOpportunityConfidence = 0.50;
        positioningValueConfidence = 0.46;
        stressPenaltyConfidence = 0.45;
    }

    public double score(GraphRouteState state) {
        if (state == null) {
            return 0.0;
        }
        return state.pickupCost() * pickupCostWeight * pickupCostConfidence
                + state.batchSynergy() * batchSynergyWeight * batchSynergyConfidence
                + state.dropCoherence() * dropCoherenceWeight * dropCoherenceConfidence
                + state.slaRisk() * slaRiskWeight * slaRiskConfidence
                + state.deadheadPenalty() * deadheadPenaltyWeight * deadheadPenaltyConfidence
                + state.futureOpportunity() * futureOpportunityWeight * futureOpportunityConfidence
                + state.positioningValue() * positioningValueWeight * positioningValueConfidence
                + state.stressPenalty() * stressPenaltyWeight * stressPenaltyConfidence;
    }

    public void applyUpdate(AdaptiveWeightUpdate update) {
        if (update == null) {
            return;
        }
        pickupCostWeight = clampWeight(pickupCostWeight + update.pickupCostDelta());
        batchSynergyWeight = clampWeight(batchSynergyWeight + update.batchSynergyDelta());
        dropCoherenceWeight = clampWeight(dropCoherenceWeight + update.dropCoherenceDelta());
        slaRiskWeight = clampWeight(slaRiskWeight + update.slaRiskDelta());
        deadheadPenaltyWeight = clampWeight(deadheadPenaltyWeight + update.deadheadPenaltyDelta());
        futureOpportunityWeight = clampWeight(futureOpportunityWeight + update.futureOpportunityDelta());
        positioningValueWeight = clampWeight(positioningValueWeight + update.positioningValueDelta());
        stressPenaltyWeight = clampWeight(stressPenaltyWeight + update.stressPenaltyDelta());

        pickupCostConfidence = clampConfidence(pickupCostConfidence + update.pickupCostConfidenceDelta());
        batchSynergyConfidence = clampConfidence(batchSynergyConfidence + update.batchSynergyConfidenceDelta());
        dropCoherenceConfidence = clampConfidence(dropCoherenceConfidence + update.dropCoherenceConfidenceDelta());
        slaRiskConfidence = clampConfidence(slaRiskConfidence + update.slaRiskConfidenceDelta());
        deadheadPenaltyConfidence = clampConfidence(deadheadPenaltyConfidence + update.deadheadPenaltyConfidenceDelta());
        futureOpportunityConfidence = clampConfidence(futureOpportunityConfidence + update.futureOpportunityConfidenceDelta());
        positioningValueConfidence = clampConfidence(positioningValueConfidence + update.positioningValueConfidenceDelta());
        stressPenaltyConfidence = clampConfidence(stressPenaltyConfidence + update.stressPenaltyConfidenceDelta());
    }

    public double confidenceMean() {
        return (pickupCostConfidence
                + batchSynergyConfidence
                + dropCoherenceConfidence
                + slaRiskConfidence
                + deadheadPenaltyConfidence
                + futureOpportunityConfidence
                + positioningValueConfidence
                + stressPenaltyConfidence) / 8.0;
    }

    public BanditPosteriorSnapshot snapshot(long updateCount, long checkpointVersion) {
        return new BanditPosteriorSnapshot(
                pickupCostWeight,
                batchSynergyWeight,
                dropCoherenceWeight,
                slaRiskWeight,
                deadheadPenaltyWeight,
                futureOpportunityWeight,
                positioningValueWeight,
                stressPenaltyWeight,
                pickupCostConfidence,
                batchSynergyConfidence,
                dropCoherenceConfidence,
                slaRiskConfidence,
                deadheadPenaltyConfidence,
                futureOpportunityConfidence,
                positioningValueConfidence,
                stressPenaltyConfidence,
                updateCount,
                checkpointVersion);
    }

    public void restore(BanditPosteriorSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        pickupCostWeight = clampWeight(snapshot.pickupCostWeight());
        batchSynergyWeight = clampWeight(snapshot.batchSynergyWeight());
        dropCoherenceWeight = clampWeight(snapshot.dropCoherenceWeight());
        slaRiskWeight = clampWeight(snapshot.slaRiskWeight());
        deadheadPenaltyWeight = clampWeight(snapshot.deadheadPenaltyWeight());
        futureOpportunityWeight = clampWeight(snapshot.futureOpportunityWeight());
        positioningValueWeight = clampWeight(snapshot.positioningValueWeight());
        stressPenaltyWeight = clampWeight(snapshot.stressPenaltyWeight());

        pickupCostConfidence = clampConfidence(snapshot.pickupCostConfidence());
        batchSynergyConfidence = clampConfidence(snapshot.batchSynergyConfidence());
        dropCoherenceConfidence = clampConfidence(snapshot.dropCoherenceConfidence());
        slaRiskConfidence = clampConfidence(snapshot.slaRiskConfidence());
        deadheadPenaltyConfidence = clampConfidence(snapshot.deadheadPenaltyConfidence());
        futureOpportunityConfidence = clampConfidence(snapshot.futureOpportunityConfidence());
        positioningValueConfidence = clampConfidence(snapshot.positioningValueConfidence());
        stressPenaltyConfidence = clampConfidence(snapshot.stressPenaltyConfidence());
    }

    private static double clampWeight(double value) {
        return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, value));
    }

    private static double clampConfidence(double value) {
        return Math.max(MIN_CONFIDENCE, Math.min(MAX_CONFIDENCE, value));
    }
}
 
