package com.routechain.graph;

import com.routechain.simulation.TravelTimeMatrixSnapshot;

/**
 * Combined road-graph and zone state snapshot used during route scoring.
 */
public record RoadGraphSnapshot(
        String snapshotId,
        String backend,
        String serviceTier,
        String pickupCellId,
        String dropCellId,
        TravelTimeMatrixSnapshot travelTimeMatrix,
        CorridorLiveState approachCorridor,
        CorridorLiveState deliveryCorridor,
        ZoneFeatureSnapshot pickupZone,
        ZoneFeatureSnapshot dropZone,
        TravelTimeDriftSnapshot approachDrift,
        TravelTimeDriftSnapshot deliveryDrift
) {
    public RoadGraphSnapshot {
        snapshotId = snapshotId == null || snapshotId.isBlank() ? "road-graph-snapshot-unknown" : snapshotId;
        backend = backend == null || backend.isBlank() ? "osm-osrm-surrogate-v1" : backend;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
        pickupCellId = pickupCellId == null || pickupCellId.isBlank() ? "cell-unknown" : pickupCellId;
        dropCellId = dropCellId == null || dropCellId.isBlank() ? "cell-unknown" : dropCellId;
        travelTimeMatrix = travelTimeMatrix == null
                ? new TravelTimeMatrixSnapshot(snapshotId, backend, java.util.Map.of(), java.util.Map.of())
                : travelTimeMatrix;
    }
}
