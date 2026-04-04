package com.routechain.simulation;

import java.util.List;

/**
 * Aggregated KPI snapshot for one scenario group.
 */
public record ScenarioGroupCertificationResult(
        String scenarioGroup,
        int sampleCount,
        List<Long> observedSeeds,
        double dispatchP95Ms,
        double dispatchP99Ms,
        double completionRate,
        double onTimeRate,
        double cancellationRate,
        double failedOrderRate,
        double realAssignmentRate,
        double deadheadDistanceRatio,
        double deadheadPerCompletedOrderKm,
        double postDropOrderHitRate,
        double deliveryCorridorQuality,
        double lastDropGoodZoneRate,
        double zigZagPenaltyAvg,
        double avgAssignedDeadheadKm,
        double fallbackExecutedShare,
        double borrowedCoverageExecutedShare,
        double selectedSubThreeInCleanRate,
        double stressDowngradeRate,
        double nextOrderIdleMinutes,
        double expectedPostCompletionEmptyKm,
        boolean routeQualityPass,
        boolean continuityPass,
        boolean stressSafetyPass,
        List<String> notes
) {
    public ScenarioGroupCertificationResult {
        scenarioGroup = scenarioGroup == null || scenarioGroup.isBlank() ? "unknown" : scenarioGroup;
        observedSeeds = observedSeeds == null ? List.of() : List.copyOf(observedSeeds);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
