package com.routechain.v2.route;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record PickupAnchorSummary(
        String schemaVersion,
        int bundleCount,
        int anchoredBundleCount,
        double averageAnchorsPerBundle,
        List<String> degradeReasons) implements SchemaVersioned {

    public static PickupAnchorSummary empty() {
        return new PickupAnchorSummary("pickup-anchor-summary/v1", 0, 0, 0.0, List.of());
    }
}
