package com.routechain.ai;

import com.routechain.domain.Driver;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;

import java.util.List;

/**
 * Immutable snapshot of a driver's local world at decision time.
 *
 * Built by {@link DriverContextBuilder} once per tick for each eligible driver.
 * Consumed by {@link DriverPlanGenerator} to produce candidate plans.
 *
 * This is the cornerstone of the driver-centric dispatch paradigm:
 * instead of finding bundles first and then matching drivers to bundles,
 * we build each driver's local view and let the driver "decide" what to pick up.
 */
public record DriverDecisionContext(
        Driver driver,

        // ── Local orders ────────────────────────────────────────────────
        /** Orders whose pickup point is reachable within the time horizon. */
        List<Order> reachableOrders,

        /** Pickup clusters formed from reachable orders (DBSCAN-like grouping). */
        List<OrderCluster> pickupClusters,

        // ── Local environment (from SpatiotemporalField) ────────────────
        /** Average traffic intensity in corridors near the driver. */
        double localTrafficIntensity,

        /** Demand intensity at the driver's current cell. */
        double localDemandIntensity,

        /** Demand-supply gap at the driver's current cell (0..1). */
        double localShortagePressure,

        /** Drivers per km² around the driver's location. */
        double localDriverDensity,

        /** Probability of a demand spike in the driver's cell within 5 min. */
        double localSpikeProbability,

        // ── Driver state ────────────────────────────────────────────────
        /** Combined opportunity score at the driver's current position. */
        double currentAttractionScore,

        /** Estimated minutes the driver will idle if no order is taken. */
        double estimatedIdleMinutes,

        // ── End-zone candidates ─────────────────────────────────────────
        /** Top attractive zones near the driver (for reposition/end-state eval). */
        List<EndZoneCandidate> endZoneCandidates
) {

    /**
     * A spatial cluster of nearby pickup points.
     * Each cluster is a potential bundle seed.
     */
    public record OrderCluster(
            String clusterId,
            List<Order> orders,
            GeoPoint centroid,
            double spreadMeters,
            double totalFee
    ) {}

    /**
     * A candidate end-zone the driver could end up in after delivery.
     * Used for continuation-value evaluation.
     */
    public record EndZoneCandidate(
            GeoPoint position,
            double attractionScore,
            double distanceKm
    ) {}
}
