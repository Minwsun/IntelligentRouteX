package com.routechain.api.dto;

public enum OrderLifecycleStage {
    CREATED,
    OFFERED,
    ACCEPTED,
    ARRIVED_PICKUP,
    PICKED_UP,
    ARRIVED_DROPOFF,
    DROPPED_OFF,
    CANCELLED,
    FAILED,
    EXPIRED
}
