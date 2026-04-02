package com.routechain.ai;

/**
 * Scored route alternative used by route-first explainability and replay.
 */
public record RouteAlternative(
        String routeId,
        String serviceTier,
        double etaP50Minutes,
        double etaP90Minutes,
        double deadheadKm,
        double rainExposure,
        double congestionExposure,
        double expectedPostCompletionEmptyKm,
        double postDropOpportunity,
        double routePriorScore,
        String reasonCode,
        int feasibleDriverCount
) {}
