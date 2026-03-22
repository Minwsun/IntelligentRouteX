package com.routechain.domain;

/**
 * Enumerations for the RouteChain domain.
 */
public final class Enums {
    private Enums() {}

    public enum OrderStatus {
        QUOTED, CONFIRMED, PENDING_ASSIGNMENT, ASSIGNED,
        PICKUP_EN_ROUTE, PICKED_UP, DROPOFF_EN_ROUTE,
        DELIVERED, CANCELLED, FAILED, EXPIRED
    }

    public enum DriverState {
        OFFLINE, ONLINE_IDLE, RESERVED, PICKUP_EN_ROUTE,
        WAITING_PICKUP, DELIVERING, REPOSITIONING
    }

    public enum WeatherProfile {
        CLEAR, LIGHT_RAIN, HEAVY_RAIN, STORM
    }

    public enum SurgeSeverity {
        NORMAL, MEDIUM, HIGH, CRITICAL
    }

    public enum MetricSeverity {
        NORMAL, WARNING, CRITICAL
    }

    public enum SimulationLifecycle {
        IDLE, LOADING, RUNNING, PAUSED, COMPLETED, ERROR
    }

    public enum AlertType {
        CONGESTION, SURGE, WEATHER, DRIVER_SHORTAGE, DISPATCH, SYSTEM
    }

    public enum VehicleType {
        MOTORBIKE, CAR
    }
}
