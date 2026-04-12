package com.routechain.simulation;

import java.util.List;
import java.util.Map;

public record CompactRuntimeStatusView(
        String mode,
        String regime,
        List<String> topFeatureContributors,
        Map<String, Double> dualPenalties,
        CalibrationSnapshot calibrationSnapshot,
        String snapshotTag,
        boolean rollbackAvailable,
        boolean learningFrozen,
        double rollingMae,
        double rollingRewardMean,
        double verdictPassRate) {

    public static CompactRuntimeStatusView empty() {
        return new CompactRuntimeStatusView(
                "COMPACT",
                "CLEAR_NORMAL",
                List.of(),
                Map.of(),
                CalibrationSnapshot.empty(),
                "snapshot-none",
                false,
                false,
                0.0,
                0.0,
                0.0);
    }
}
