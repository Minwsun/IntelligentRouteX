package com.routechain.simulation;

public record CalibrationSnapshot(
        double etaResidualMaeMinutes,
        double cancelCalibrationGap,
        double postDropHitCalibrationGap,
        double nextIdleMaeMinutes,
        double emptyKmMae,
        long etaSamples,
        long cancelSamples,
        long postDropSamples) {

    public static CalibrationSnapshot empty() {
        return new CalibrationSnapshot(0.0, 0.0, 0.0, 0.0, 0.0, 0L, 0L, 0L);
    }
}
