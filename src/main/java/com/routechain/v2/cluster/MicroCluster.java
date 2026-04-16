package com.routechain.v2.cluster;

import com.routechain.domain.GeoPoint;
import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record MicroCluster(
        String schemaVersion,
        String clusterId,
        List<String> orderIds,
        List<String> coreOrderIds,
        List<String> boundaryOrderIds,
        GeoPoint pickupCentroid,
        double dominantDropDirection,
        String corridorSignature,
        long timeSpanMinutes) implements SchemaVersioned {
}

