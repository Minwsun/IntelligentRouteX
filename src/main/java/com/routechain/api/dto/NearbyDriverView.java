package com.routechain.api.dto;

public record NearbyDriverView(
        String driverId,
        boolean available,
        double lat,
        double lng,
        double distanceKm,
        String state,
        String activeOfferId
) {}
