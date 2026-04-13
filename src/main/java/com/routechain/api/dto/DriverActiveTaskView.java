package com.routechain.api.dto;

import java.util.List;

public record DriverActiveTaskView(
        String driverId,
        String taskId,
        String orderId,
        String status,
        OrderLifecycleStage lifecycleStage,
        String serviceTier,
        String customerId,
        Double etaMinutes,
        MapPointView currentLocation,
        MapPointView runtimeDriverLocation,
        MapPointView pickup,
        MapPointView dropoff,
        List<MapPointView> routePolyline,
        RouteSourceView routeSource,
        String routeGeneratedAt,
        List<MapPointView> activeRoutePolyline,
        RouteSourceView activeRouteSource,
        String activeRouteGeneratedAt,
        List<MapPointView> remainingRoutePreviewPolyline,
        RoutePreviewSourceView remainingRoutePreviewSource,
        String assignedAt,
        String arrivedPickupAt,
        String pickedUpAt,
        String arrivedDropoffAt
) {}
