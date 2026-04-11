package com.routechain.api.dto;

import java.util.List;

public record LiveMapSnapshot(
        String mode,
        String subjectId,
        String orderId,
        String driverId,
        String status,
        List<NearbyDriverView> nearbyDrivers,
        MapPointView pickup,
        MapPointView dropoff,
        NearbyDriverView assignedDriver,
        List<MapPointView> routePolyline,
        RouteSourceView routeSource,
        String routeGeneratedAt
) {}
