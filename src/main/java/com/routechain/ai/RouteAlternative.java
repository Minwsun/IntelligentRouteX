package com.routechain.ai;

/**
 * Scored route alternative used by route-first explainability and replay.
 */
public record RouteAlternative(
        String routeId,
        double etaP50Minutes,
        double etaP90Minutes,
        double deadheadKm,
        double rainExposure,
        double congestionExposure,
        String reasonCode,
        int feasibleDriverCount
) {}
