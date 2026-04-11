package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

public record CompactBenchmarkSummary(
        Instant generatedAt,
        String scope,
        String baselinePolicy,
        List<CompactBenchmarkCase> cases,
        double compactCompletionDeltaVsBaseline,
        double compactOnTimeDeltaVsBaseline,
        double compactDeadheadDeltaVsBaseline,
        double compactPostDropHitDeltaVsBaseline,
        double compactEmptyKmDeltaVsBaseline,
        double compactCompletionDeltaVsOmega,
        double compactDeadheadDeltaVsOmega) {
}
