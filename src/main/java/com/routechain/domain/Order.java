package com.routechain.domain;

import java.time.Instant;

public record Order(
        String orderId,
        GeoPoint pickupPoint,
        GeoPoint dropoffPoint,
        Instant createdAt,
        Instant readyAt,
        int promisedEtaMinutes,
        boolean urgent) {
}

