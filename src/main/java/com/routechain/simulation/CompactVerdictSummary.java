package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

public record CompactVerdictSummary(
        Instant generatedAt,
        String lane,
        String scope,
        boolean completionPass,
        boolean onTimePass,
        boolean deadheadPass,
        boolean postDropHitPass,
        boolean emptyKmPass,
        boolean noSevereRegressionPass,
        boolean overallPass,
        double completionDeltaVsBaseline,
        double onTimeDeltaVsBaseline,
        double deadheadImprovementPctVsBaseline,
        double postDropHitDeltaVsBaseline,
        double emptyKmImprovementPctVsBaseline,
        double batchChosenWhenEligibleRate,
        List<String> notes,
        CompactBenchmarkSummary benchmarkSummary) {
}
