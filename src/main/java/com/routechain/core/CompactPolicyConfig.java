package com.routechain.core;

public record CompactPolicyConfig(
        int defaultCandidateCap,
        int stressCandidateCap,
        double onTimeFloor,
        double deadheadCapKm,
        double detourCapKm,
        double merchantWaitCapMinutes,
        boolean batchFirstEnabled,
        double batchDominanceTolerance,
        double emptyRunPriorityWeight,
        double minEmptyKmAdvantageKm,
        double minPostDropDemandAdvantage,
        long postDropWindowTicks,
        int rollingPenaltyWindow,
        double gradientClip,
        double maxFeatureUpdate,
        double noisyOutcomeErrorThreshold,
        int supportSamplesForFullConfidence) {

    public static CompactPolicyConfig defaults() {
        return new CompactPolicyConfig(
                6,
                8,
                0.58,
                5.0,
                3.0,
                10.0,
                true,
                0.20,
                1.25,
                0.15,
                0.04,
                240L,
                20,
                0.35,
                0.06,
                0.75,
                20);
    }
}
