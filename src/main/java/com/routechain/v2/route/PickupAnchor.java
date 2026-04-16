package com.routechain.v2.route;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record PickupAnchor(
        String schemaVersion,
        String bundleId,
        String bundleOrderSetSignature,
        String anchorOrderId,
        int anchorRank,
        double score,
        List<String> reasons) implements SchemaVersioned {
}
