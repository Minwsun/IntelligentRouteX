package com.routechain.simulation;

import java.util.Map;

/**
 * Lightweight matrix snapshot for batch route construction and audit.
 */
public record TravelTimeMatrixSnapshot(
        String matrixId,
        String backend,
        Map<String, Double> travelMinutes,
        Map<String, Double> travelDistanceKm
) {
    public TravelTimeMatrixSnapshot {
        matrixId = matrixId == null || matrixId.isBlank() ? "matrix-unknown" : matrixId;
        backend = backend == null || backend.isBlank() ? "osrm-or-heuristic" : backend;
        travelMinutes = travelMinutes == null ? Map.of() : Map.copyOf(travelMinutes);
        travelDistanceKm = travelDistanceKm == null ? Map.of() : Map.copyOf(travelDistanceKm);
    }
}
