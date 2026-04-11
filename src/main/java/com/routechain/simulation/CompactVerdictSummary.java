package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

public record CompactVerdictSummary(
        Instant generatedAt,
        String scope,
        boolean completionPass,
        boolean onTimePass,
        boolean deadheadPass,
        boolean postDropHitPass,
        boolean emptyKmPass,
        boolean noSevereRegressionPass,
        boolean overallPass,
        List<String> notes,
        CompactBenchmarkSummary benchmarkSummary) {
}
