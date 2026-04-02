package com.routechain.simulation;

/**
 * Acceptance comparison between baseline and candidate runs.
 */
public record ScenarioAcceptanceDelta(
        boolean measurementPassA,
        boolean measurementPassB,
        boolean performancePassA,
        boolean performancePassB,
        boolean intelligencePassA,
        boolean intelligencePassB,
        boolean safetyPassA,
        boolean safetyPassB,
        boolean overallPassA,
        boolean overallPassB,
        String notes
) {
    public static ScenarioAcceptanceDelta compare(ScenarioAcceptanceResult baseline,
                                                  ScenarioAcceptanceResult candidate) {
        ScenarioAcceptanceResult left = baseline == null ? ScenarioAcceptanceResult.empty() : baseline;
        ScenarioAcceptanceResult right = candidate == null ? ScenarioAcceptanceResult.empty() : candidate;
        String note = (left.notes() == null ? "" : left.notes())
                + ((left.notes() == null || left.notes().isBlank() || right.notes() == null || right.notes().isBlank()) ? "" : " | ")
                + (right.notes() == null ? "" : right.notes());
        return new ScenarioAcceptanceDelta(
                left.measurementPass(),
                right.measurementPass(),
                left.performancePass(),
                right.performancePass(),
                left.intelligencePass(),
                right.intelligencePass(),
                left.safetyPass(),
                right.safetyPass(),
                left.overallPass(),
                right.overallPass(),
                note.trim()
        );
    }
}
