package com.routechain.simulation;

/**
 * Stable run/session identity owned by {@link SimulationEngine}.
 */
public record RunIdentity(
        String sessionId,
        String runId
) {}
