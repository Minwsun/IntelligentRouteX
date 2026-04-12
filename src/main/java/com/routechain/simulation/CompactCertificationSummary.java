package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

public record CompactCertificationSummary(
        Instant generatedAt,
        String lane,
        String scope,
        String verdict,
        boolean completionPass,
        boolean onTimePass,
        boolean deadheadPass,
        boolean emptyKmPass,
        boolean postDropHitPass,
        boolean overallPass,
        double completionDeltaVsOmega,
        double onTimeDeltaVsOmega,
        double deadheadDeltaVsOmega,
        double emptyKmDeltaVsOmega,
        double postDropHitDeltaVsOmega,
        CalibrationSnapshot compactCalibrationSnapshot,
        List<CompactCertificationRegimeResult> regimeResults,
        List<String> notes,
        CompactBenchmarkSummary benchmarkSummary) {
}
