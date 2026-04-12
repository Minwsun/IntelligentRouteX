package com.routechain.simulation;

import java.util.List;

/**
 * Summarizes one route-quality blocker bucket from compare artifacts.
 */
public record RouteQualityBlockerBucketSummary(
        String bucketName,
        String evidenceScope,
        int sampleCount,
        double overallGainPercentMean,
        double completionDeltaMean,
        double onTimeDeltaMean,
        double etaBiasMinutesMean,
        double cancelDeltaMean,
        double deadheadDeltaMean,
        double deadheadPerCompletedOrderKmDeltaMean,
        double postDropHitDeltaMean,
        double landingDeltaMean,
        double emptyAfterKmDeltaMean,
        double nextIdleMinutesDeltaMean,
        double fallbackShareDeltaMean,
        double borrowedShareDeltaMean,
        double stressDowngradeDeltaMean,
        List<String> blockerReasons,
        List<String> notes
) {
    public RouteQualityBlockerBucketSummary {
        bucketName = bucketName == null || bucketName.isBlank() ? "UNKNOWN" : bucketName;
        evidenceScope = evidenceScope == null || evidenceScope.isBlank() ? "artifact" : evidenceScope;
        blockerReasons = blockerReasons == null ? List.of() : List.copyOf(blockerReasons);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
