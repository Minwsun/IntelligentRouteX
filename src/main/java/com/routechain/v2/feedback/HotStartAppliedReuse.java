package com.routechain.v2.feedback;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record HotStartAppliedReuse(
        String schemaVersion,
        boolean pairClusterReused,
        boolean bundlePoolReused,
        boolean routeProposalPoolReused,
        int reusedBundleCount,
        int reusedRouteProposalCount,
        List<String> degradeReasons) implements SchemaVersioned {
}
