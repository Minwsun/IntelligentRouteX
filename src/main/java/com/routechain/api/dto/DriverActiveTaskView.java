package com.routechain.api.dto;

import java.util.List;

public record DriverActiveTaskView(
        String driverId,
        String taskId,
        String orderId,
        String status,
        String serviceTier,
        String customerId,
        Double etaMinutes,
        MapPointView currentLocation,
        MapPointView pickup,
        MapPointView dropoff,
        List<MapPointView> routePolyline
) {}
