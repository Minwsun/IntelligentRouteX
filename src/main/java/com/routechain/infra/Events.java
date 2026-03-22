package com.routechain.infra;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;
import java.time.Instant;

/**
 * All domain events published through the EventBus.
 */
public final class Events {
    private Events() {}

    // ── Simulation lifecycle ────────────────────────────────────────────
    public record SimulationStarted(Instant timestamp) {}
    public record SimulationStopped(Instant timestamp) {}
    public record SimulationTick(long tickNumber, Instant timestamp) {}
    public record SimulationReset(Instant timestamp) {}

    // ── Order events ────────────────────────────────────────────────────
    public record OrderCreated(Order order) {}
    public record OrderAssigned(String orderId, String driverId) {}
    public record OrderPickedUp(String orderId) {}
    public record OrderDelivered(String orderId) {}
    public record OrderCancelled(String orderId, String reason) {}
    public record OrderFailed(String orderId, String reason) {}

    // ── Driver events ───────────────────────────────────────────────────
    public record DriverLocationUpdated(String driverId, GeoPoint location, double speedKmh) {}
    public record DriverStateChanged(String driverId, DriverState oldState, DriverState newState) {}
    public record DriverOnline(String driverId) {}
    public record DriverOffline(String driverId) {}

    // ── Traffic events ──────────────────────────────────────────────────
    public record TrafficUpdated(String regionId, double congestionScore) {}
    public record TrafficSegmentUpdated(String segmentId, GeoPoint from, GeoPoint to, double severity) {}

    // ── Weather events ──────────────────────────────────────────────────
    public record WeatherChanged(String regionId, WeatherProfile profile, double intensity) {}

    // ── Surge / Alert events ────────────────────────────────────────────
    public record SurgeDetected(String regionId, double surgeScore, SurgeSeverity severity, String cause) {}
    public record DriverShortageDetected(String regionId, double shortageRatio) {}
    public record AlertRaised(String id, AlertType type, String title, String description,
                              SurgeSeverity severity, String regionId, Instant timestamp) {}

    // ── Dispatch events ─────────────────────────────────────────────────
    public record DispatchDecision(String orderId, String driverId, double score,
                                    double etaMinutes, double deadheadKm, double confidence) {}
    public record ReDispatchTriggered(String orderId, String reason) {}

    // ── AI Insight events ───────────────────────────────────────────────
    public record AiInsight(String title, String description, String recommendation,
                            Instant timestamp) {}

    // ── Timeline snapshot event ──────────────────────────────────────────
    public record TimelineSnapshot(String formattedTime, double avgTraffic,
                                    double weatherIntensity, double maxSurge,
                                    int pendingOrders, int activeDrivers,
                                    String roadDescription) {}

    // ── Metrics snapshot ────────────────────────────────────────────────
    public record MetricsSnapshot(double onTimePercent, double deadheadPercent,
                                   double netPerHour, double avgEtaMinutes,
                                   int activeOrders, int activeDrivers,
                                   int completedOrders, int cancelledOrders) {}
}
