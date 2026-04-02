package com.routechain.ai;

/**
 * Execution-first policy decomposition for one selected plan.
 */
public record PolicyEvaluationRecord(
        String serviceTier,
        double executionScore,
        double continuationScore,
        double coverageScore,
        double routePriorScore,
        String selectedBucket,
        boolean fallbackSelected,
        boolean borrowedSelected,
        double neuralPriorScore,
        boolean neuralPriorUsed,
        String rejectionReason
) {}
