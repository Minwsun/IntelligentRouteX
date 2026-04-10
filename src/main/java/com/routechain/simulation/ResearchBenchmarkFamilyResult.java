package com.routechain.simulation;

import java.util.List;

/**
 * Family-level summary for public research benchmark evidence.
 */
public record ResearchBenchmarkFamilyResult(
        String familyId,
        int sampleCount,
        double overallGainPercentMean,
        double completionDeltaMean,
        double deadheadDeltaMean,
        double postDropDeltaMean,
        double safetyPassRate,
        boolean pass,
        List<String> notes
) {
    public ResearchBenchmarkFamilyResult {
        familyId = familyId == null || familyId.isBlank() ? "unknown" : familyId;
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
