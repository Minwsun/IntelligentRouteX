package com.routechain.simulation;

/**
 * Per-scenario acceptance verdict for one run.
 */
public record ScenarioAcceptanceResult(
        String scenarioName,
        String serviceTier,
        String environmentProfile,
        boolean measurementPass,
        boolean performancePass,
        boolean intelligencePass,
        boolean safetyPass,
        boolean overallPass,
        String primaryVerdict,
        String secondaryVerdict,
        String notes
) {
    public static ScenarioAcceptanceResult empty() {
        return new ScenarioAcceptanceResult(
                "unknown",
                "instant",
                "unknown",
                false,
                false,
                false,
                false,
                false,
                "UNASSESSED",
                "UNASSESSED",
                ""
        );
    }
}
