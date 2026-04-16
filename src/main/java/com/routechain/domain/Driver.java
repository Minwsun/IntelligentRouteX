package com.routechain.domain;

public record Driver(
        String driverId,
        GeoPoint currentLocation) {
}

