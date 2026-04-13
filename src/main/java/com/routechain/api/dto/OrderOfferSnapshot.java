package com.routechain.api.dto;

public record OrderOfferSnapshot(
        OrderOfferStage stage,
        String activeBatchId,
        int activeWave,
        int totalWaves,
        boolean reofferEligible,
        boolean pendingOffersPresent,
        String latestResolutionReason,
        OfferWaveSummary latestWave,
        AssignmentLockView assignmentLock
) {}
