package com.routechain.v2.bundle;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DispatchBundleStage(
        String schemaVersion,
        List<BoundaryExpansion> boundaryExpansions,
        BoundaryExpansionSummary boundaryExpansionSummary,
        List<BundleCandidate> bundleCandidates,
        BundlePoolSummary bundlePoolSummary,
        List<String> degradeReasons) implements SchemaVersioned {
}
