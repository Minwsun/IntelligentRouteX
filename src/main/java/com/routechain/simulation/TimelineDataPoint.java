package com.routechain.simulation;

/**
 * Snapshot of simulation state at a single tick, used by the timeline UI.
 */
public record TimelineDataPoint(
        long tick,
        int hour,
        int minute,
        String formattedTime,
        double avgTrafficSeverity,
        double weatherIntensity,
        double maxSurgeScore,
        int pendingOrders,
        int activeDrivers,
        String roadDescription
) {}
