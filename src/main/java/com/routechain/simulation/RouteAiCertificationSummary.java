package com.routechain.simulation;

import java.util.List;

/**
 * Pass/fail summary for the smoke certification lane.
 */
public record RouteAiCertificationSummary(
        String schemaVersion,
        String laneName,
        String profileName,
        String scenarioName,
        String baselinePolicy,
        String candidatePolicy,
        boolean routeRegressionPass,
        boolean measurementValid,
        double dispatchP95Ms,
        double dispatchP99Ms,
        double dispatchP95TargetMs,
        double dispatchP99TargetMs,
        boolean dispatchP95Pass,
        boolean dispatchP99Pass,
        double overallGainPercent,
        double completionDelta,
        double deadheadDistanceRatioDelta,
        double postDropOrderHitRateDelta,
        boolean gainPass,
        boolean completionPass,
        boolean deadheadPass,
        boolean safetyPass,
        boolean overallPass,
        String dominantDispatchStage,
        List<String> notes
) {
    public RouteAiCertificationSummary {
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
