package com.routechain.v2.bundle;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record BundleCandidate(
        String schemaVersion,
        String bundleId,
        BundleFamily family,
        List<String> orderIds,
        String orderSetSignature,
        String seedOrderId,
        String corridorSignature,
        double score,
        boolean feasible,
        List<String> degradeReasons) implements SchemaVersioned {
}
