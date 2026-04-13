package com.routechain.api.dto;

public record UserRealtimeSnapshot(
        String customerId,
        TripTrackingView activeTrip,
        LiveMapSnapshot mapSnapshot
) {}
