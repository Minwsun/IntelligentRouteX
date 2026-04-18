package com.routechain.v2.bundle;

import com.routechain.v2.MlStageMetadata;
import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.DispatchStageLatency;
import com.routechain.v2.HotStartReuseSummary;

import java.util.List;

public record DispatchBundleStage(
        String schemaVersion,
        List<BoundaryExpansion> boundaryExpansions,
        BoundaryExpansionSummary boundaryExpansionSummary,
        List<BundleCandidate> bundleCandidates,
        BundlePoolSummary bundlePoolSummary,
        HotStartReuseSummary hotStartReuseSummary,
        List<DispatchStageLatency> stageLatencies,
        List<MlStageMetadata> mlStageMetadata,
        List<String> degradeReasons) implements SchemaVersioned {
}
