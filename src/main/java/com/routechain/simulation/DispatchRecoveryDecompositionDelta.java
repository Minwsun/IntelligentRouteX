package com.routechain.simulation;

/**
 * Delta between two recovery decompositions (B - A).
 */
public record DispatchRecoveryDecompositionDelta(
        int generatedOrderPlanCountDelta,
        int generatedWaveCandidateCountDelta,
        int generatedExtensionCandidateCountDelta,
        int generatedFallbackCandidateCountDelta,
        int generatedHoldCandidateCountDelta,
        int shortlistedWaveCountDelta,
        int shortlistedFallbackCountDelta,
        int shortlistedHoldCountDelta,
        int solverSelectedWaveCountDelta,
        int solverSelectedExtensionCountDelta,
        int solverSelectedFallbackCountDelta,
        int solverSelectedHoldCountDelta,
        int fallbackInjectedCountDelta,
        int executedWaveCountDelta,
        int executedExtensionCountDelta,
        int executedFallbackCountDelta,
        int executedBorrowedCountDelta,
        int executedLocalCoverageCountDelta,
        int holdConvertedToWaveCountDelta,
        int holdExpiredToFallbackCountDelta,
        int prePickupAugmentConvertedCountDelta,
        int waveRejectedByDeadheadCountDelta,
        int waveRejectedBySlaCountDelta,
        int waveRejectedByConstraintCountDelta,
        int holdSuppressedByFallbackCountDelta,
        int borrowedPreferredOverLocalCountDelta
) {
    public static DispatchRecoveryDecompositionDelta compare(
            DispatchRecoveryDecomposition baseline,
            DispatchRecoveryDecomposition candidate) {
        DispatchRecoveryDecomposition a = baseline == null
                ? DispatchRecoveryDecomposition.empty()
                : baseline;
        DispatchRecoveryDecomposition b = candidate == null
                ? DispatchRecoveryDecomposition.empty()
                : candidate;
        return new DispatchRecoveryDecompositionDelta(
                b.generatedOrderPlanCount() - a.generatedOrderPlanCount(),
                b.generatedWaveCandidateCount() - a.generatedWaveCandidateCount(),
                b.generatedExtensionCandidateCount() - a.generatedExtensionCandidateCount(),
                b.generatedFallbackCandidateCount() - a.generatedFallbackCandidateCount(),
                b.generatedHoldCandidateCount() - a.generatedHoldCandidateCount(),
                b.shortlistedWaveCount() - a.shortlistedWaveCount(),
                b.shortlistedFallbackCount() - a.shortlistedFallbackCount(),
                b.shortlistedHoldCount() - a.shortlistedHoldCount(),
                b.solverSelectedWaveCount() - a.solverSelectedWaveCount(),
                b.solverSelectedExtensionCount() - a.solverSelectedExtensionCount(),
                b.solverSelectedFallbackCount() - a.solverSelectedFallbackCount(),
                b.solverSelectedHoldCount() - a.solverSelectedHoldCount(),
                b.fallbackInjectedCount() - a.fallbackInjectedCount(),
                b.executedWaveCount() - a.executedWaveCount(),
                b.executedExtensionCount() - a.executedExtensionCount(),
                b.executedFallbackCount() - a.executedFallbackCount(),
                b.executedBorrowedCount() - a.executedBorrowedCount(),
                b.executedLocalCoverageCount() - a.executedLocalCoverageCount(),
                b.holdConvertedToWaveCount() - a.holdConvertedToWaveCount(),
                b.holdExpiredToFallbackCount() - a.holdExpiredToFallbackCount(),
                b.prePickupAugmentConvertedCount() - a.prePickupAugmentConvertedCount(),
                b.waveRejectedByDeadheadCount() - a.waveRejectedByDeadheadCount(),
                b.waveRejectedBySlaCount() - a.waveRejectedBySlaCount(),
                b.waveRejectedByConstraintCount() - a.waveRejectedByConstraintCount(),
                b.holdSuppressedByFallbackCount() - a.holdSuppressedByFallbackCount(),
                b.borrowedPreferredOverLocalCount() - a.borrowedPreferredOverLocalCount()
        );
    }
}
