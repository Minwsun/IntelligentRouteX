package com.routechain.v2.bundle;

import com.routechain.v2.SchemaVersioned;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record BundlePoolSummary(
        String schemaVersion,
        int candidateCount,
        int retainedCount,
        Map<BundleFamily, Integer> familyCounts,
        int maxBundleSize,
        List<String> degradeReasons) implements SchemaVersioned {

    public static BundlePoolSummary empty() {
        return new BundlePoolSummary(
                "bundle-pool-summary/v1",
                0,
                0,
                new EnumMap<>(BundleFamily.class),
                0,
                List.of());
    }
}
