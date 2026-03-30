package com.routechain.simulation;

/**
 * Recovery funnel counters for one run.
 * Rates are derived downstream in reports/exporters.
 */
public record DispatchRecoveryDecomposition(
        int generatedOrderPlanCount,
        int generatedWaveCandidateCount,
        int generatedExtensionCandidateCount,
        int generatedFallbackCandidateCount,
        int generatedHoldCandidateCount,
        int shortlistedWaveCount,
        int shortlistedFallbackCount,
        int shortlistedHoldCount,
        int solverSelectedWaveCount,
        int solverSelectedExtensionCount,
        int solverSelectedFallbackCount,
        int solverSelectedHoldCount,
        int fallbackInjectedCount,
        int executedWaveCount,
        int executedExtensionCount,
        int executedFallbackCount,
        int executedBorrowedCount,
        int executedLocalCoverageCount,
        int holdConvertedToWaveCount,
        int holdExpiredToFallbackCount,
        int prePickupAugmentConvertedCount,
        int waveRejectedByDeadheadCount,
        int waveRejectedBySlaCount,
        int waveRejectedByConstraintCount,
        int holdSuppressedByFallbackCount,
        int borrowedPreferredOverLocalCount
) {
    public static DispatchRecoveryDecomposition empty() {
        return new DispatchRecoveryDecomposition(
                0, 0, 0, 0, 0,
                0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0, 0, 0,
                0, 0, 0,
                0, 0, 0, 0, 0);
    }

    public DispatchRecoveryDecomposition plus(DispatchRecoveryDecomposition other) {
        if (other == null) {
            return this;
        }
        return new DispatchRecoveryDecomposition(
                generatedOrderPlanCount + other.generatedOrderPlanCount,
                generatedWaveCandidateCount + other.generatedWaveCandidateCount,
                generatedExtensionCandidateCount + other.generatedExtensionCandidateCount,
                generatedFallbackCandidateCount + other.generatedFallbackCandidateCount,
                generatedHoldCandidateCount + other.generatedHoldCandidateCount,
                shortlistedWaveCount + other.shortlistedWaveCount,
                shortlistedFallbackCount + other.shortlistedFallbackCount,
                shortlistedHoldCount + other.shortlistedHoldCount,
                solverSelectedWaveCount + other.solverSelectedWaveCount,
                solverSelectedExtensionCount + other.solverSelectedExtensionCount,
                solverSelectedFallbackCount + other.solverSelectedFallbackCount,
                solverSelectedHoldCount + other.solverSelectedHoldCount,
                fallbackInjectedCount + other.fallbackInjectedCount,
                executedWaveCount + other.executedWaveCount,
                executedExtensionCount + other.executedExtensionCount,
                executedFallbackCount + other.executedFallbackCount,
                executedBorrowedCount + other.executedBorrowedCount,
                executedLocalCoverageCount + other.executedLocalCoverageCount,
                holdConvertedToWaveCount + other.holdConvertedToWaveCount,
                holdExpiredToFallbackCount + other.holdExpiredToFallbackCount,
                prePickupAugmentConvertedCount + other.prePickupAugmentConvertedCount,
                waveRejectedByDeadheadCount + other.waveRejectedByDeadheadCount,
                waveRejectedBySlaCount + other.waveRejectedBySlaCount,
                waveRejectedByConstraintCount + other.waveRejectedByConstraintCount,
                holdSuppressedByFallbackCount + other.holdSuppressedByFallbackCount,
                borrowedPreferredOverLocalCount + other.borrowedPreferredOverLocalCount
        );
    }

    public double waveSeenRate() {
        return percent(generatedWaveCandidateCount + generatedExtensionCandidateCount, generatedOrderPlanCount);
    }

    public double waveShortlistedRate() {
        return percent(shortlistedWaveCount, generatedWaveCandidateCount + generatedExtensionCandidateCount);
    }

    public double waveSelectionRate() {
        return percent(solverSelectedWaveCount + solverSelectedExtensionCount, shortlistedWaveCount);
    }

    public double waveExecutionRate() {
        return percent(executedWaveCount + executedExtensionCount, solverSelectedWaveCount + solverSelectedExtensionCount);
    }

    public double holdKeepRate() {
        return percent(shortlistedHoldCount, generatedHoldCandidateCount);
    }

    public double holdConversionRate() {
        return percent(holdConvertedToWaveCount, shortlistedHoldCount);
    }

    public double fallbackDirectRate() {
        return percent(executedFallbackCount, executedFallbackCount + executedWaveCount + executedExtensionCount);
    }

    public double borrowedSelectionRate() {
        return percent(executedBorrowedCount, executedFallbackCount + executedWaveCount + executedExtensionCount);
    }

    public double localCoverageExecutionRate() {
        return percent(executedLocalCoverageCount, executedFallbackCount + executedWaveCount + executedExtensionCount);
    }

    private static double percent(double numerator, double denominator) {
        if (denominator <= 0.0) {
            return 0.0;
        }
        return numerator * 100.0 / denominator;
    }
}
