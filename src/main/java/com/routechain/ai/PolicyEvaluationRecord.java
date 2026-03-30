package com.routechain.ai;

/**
 * Execution-first policy decomposition for one selected plan.
 */
public record PolicyEvaluationRecord(
        double executionScore,
        double futureScore,
        boolean fallbackSelected,
        boolean borrowedSelected
) {}
