package com.routechain.core;

public record CompactPolicyConfig(
        int defaultCandidateCap,
        int stressCandidateCap,
        double onTimeFloor,
        double deadheadCapKm,
        double detourCapKm,
        double merchantWaitCapMinutes,
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
                240L,
                20,
                0.35,
                0.06,
                0.75,
                20);
    }
}
