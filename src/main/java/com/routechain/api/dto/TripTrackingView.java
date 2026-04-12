package com.routechain.api.dto;

import java.util.List;

public record TripTrackingView(
        String orderId,
        String customerId,
        String status,
        String serviceTier,
        double quotedFee,
        String assignedDriverId,
        String offerBatchId,
        String stage,
        Double etaMinutes,
        MapPointView pickup,
        MapPointView dropoff,
        NearbyDriverView assignedDriver,
        MapPointView runtimeDriverLocation,
        List<MapPointView> routePolyline,
        RouteSourceView routeSource,
        String routeGeneratedAt,
        List<MapPointView> activeRoutePolyline,
        RouteSourceView activeRouteSource,
        String activeRouteGeneratedAt,
        List<MapPointView> remainingRoutePreviewPolyline,
        RoutePreviewSourceView remainingRoutePreviewSource
) {}
