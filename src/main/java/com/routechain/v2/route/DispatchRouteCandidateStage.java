package com.routechain.v2.route;

import com.routechain.v2.MlStageMetadata;
import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.DispatchStageLatency;

import java.util.List;

public record DispatchRouteCandidateStage(
        String schemaVersion,
        List<PickupAnchor> pickupAnchors,
        PickupAnchorSummary pickupAnchorSummary,
        List<DriverCandidate> driverCandidates,
        DriverShortlistSummary driverShortlistSummary,
        List<DispatchStageLatency> stageLatencies,
        List<MlStageMetadata> mlStageMetadata,
        List<String> degradeReasons) implements SchemaVersioned {
}
