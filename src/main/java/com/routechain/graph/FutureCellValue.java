package com.routechain.graph;

/**
 * Forecast-aware value of landing in a future H3 cell.
 */
public record FutureCellValue(
        String cellId,
        String serviceTier,
        int horizonMinutes,
        double demandScore,
        double postDropOpportunity,
        double emptyRisk,
        double graphCentralityScore,
        double futureValueScore,
        String rationale
) {
    public FutureCellValue {
        cellId = cellId == null || cellId.isBlank() ? "cell-unknown" : cellId;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
        rationale = rationale == null ? "" : rationale;
    }
}
