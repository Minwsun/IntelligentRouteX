package com.routechain.v2.route;

import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;

public record PickupAnchor(
        String anchorId,
        Order anchorOrder,
        GeoPoint location,
        double score) {
}
