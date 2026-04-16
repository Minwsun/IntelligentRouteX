package com.routechain.v2.route;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DriverShortlistSummary(
        String schemaVersion,
        int bundleCount,
        int anchorCount,
        int shortlistedDriverCount,
        int rerankedDriverCount,
        List<String> degradeReasons) implements SchemaVersioned {

    public static DriverShortlistSummary empty() {
        return new DriverShortlistSummary("driver-shortlist-summary/v1", 0, 0, 0, 0, List.of());
    }
}
