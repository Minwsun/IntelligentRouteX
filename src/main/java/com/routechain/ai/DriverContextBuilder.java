package com.routechain.ai;

import com.routechain.ai.DriverDecisionContext.EndZoneCandidate;
import com.routechain.ai.DriverDecisionContext.OrderCluster;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds {@link DriverDecisionContext} for each eligible driver per tick.
 *
 * Core responsibilities:
 *   1. Use {@link NearbyOrderIndexer} to find reachable pending orders
 *   2. Cluster nearby pickups into candidate bundles (DBSCAN-simplified)
 *   3. Query {@link SpatiotemporalField} for local environment metrics
 *   4. Identify top attractive end-zones for reposition / end-state evaluation
 *
 * This class replaces the "find bundles globally, then match drivers" approach
 * with a "per-driver local world" approach.
 */
public class DriverContextBuilder {

    /** Maximum minutes a driver is willing to travel for pickup. */
    private static final double REACHABILITY_HORIZON_MINUTES = 8.0;

    /** Maximum distance (meters) between pickups to be in the same cluster. */
    private static final double CLUSTER_RADIUS_METERS = 1500.0;

    /** Maximum end-zone distance to consider (km). */
    private static final double MAX_END_ZONE_KM = 3.0;

    /** Number of top end-zone candidates to include in context. */
    private static final int TOP_END_ZONES = 3;

    private final NearbyOrderIndexer orderIndex;
    private final SpatiotemporalField field;

    public DriverContextBuilder(NearbyOrderIndexer orderIndex,
                                 SpatiotemporalField field) {
        this.orderIndex = orderIndex;
        this.field = field;
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Rebuild the spatial order index. Must be called once per tick
     * before any calls to {@link #build}.
     *
     * @param pendingOrders orders in PENDING_ASSIGNMENT state
     */
    public void rebuildIndex(List<Order> pendingOrders) {
        orderIndex.rebuild(pendingOrders);
    }

    /**
     * Build a complete local-world snapshot for a single driver.
     *
     * @param driver            the driver to build context for
     * @param trafficIntensity  global traffic intensity [0..1]
     * @param weather           current weather profile
     * @param simulatedHour     current hour (0-23)
     * @return immutable context record
     */
    public DriverDecisionContext build(Driver driver,
                                        double trafficIntensity,
                                        WeatherProfile weather,
                                        int simulatedHour) {

        GeoPoint pos = driver.getCurrentLocation();
        double speedKmh = estimateSpeed(trafficIntensity, weather);

        // 1. Find reachable orders
        List<Order> reachable = orderIndex.findReachable(
                pos, speedKmh, REACHABILITY_HORIZON_MINUTES);

        // 2. Cluster pickups (DBSCAN-simplified)
        List<OrderCluster> clusters = clusterPickups(reachable);

        // 3. Query local environment from SpatiotemporalField
        double localDemand = field.getDemandAt(pos);
        double localShortage = field.getShortageAt(pos);
        double localDensity = field.getDriverDensityAt(pos);
        double localSpike = field.getSpikeAt(pos);
        double localAttraction = field.getAttractionAt(pos);

        // 4. Estimate local traffic (blend global + corridor proximity)
        double localTraffic = estimateLocalTraffic(pos, trafficIntensity);

        // 5. End-zone candidates
        List<EndZoneCandidate> endZones = findTopEndZones(pos);

        // 6. Estimated idle time if driver takes no order
        double idleMinutes = estimateIdleTime(localDemand, localShortage);

        return new DriverDecisionContext(
                driver, reachable, clusters,
                localTraffic, localDemand, localShortage,
                localDensity, localSpike,
                localAttraction, idleMinutes, endZones
        );
    }

    // ── Clustering ──────────────────────────────────────────────────────

    /**
     * Simplified DBSCAN — single-pass greedy clustering.
     *
     * Algorithm:
     *   1. Sort orders by fee descending (high-value seeds first)
     *   2. For each unclustered order, start a new cluster
     *   3. Add any unclustered order within CLUSTER_RADIUS_METERS
     *   4. Recompute centroid after each addition
     */
    private List<OrderCluster> clusterPickups(List<Order> orders) {
        if (orders.isEmpty()) return List.of();

        // Sort by fee desc — high-value orders seed clusters
        List<Order> sorted = new ArrayList<>(orders);
        sorted.sort((a, b) -> Double.compare(b.getQuotedFee(), a.getQuotedFee()));

        boolean[] assigned = new boolean[sorted.size()];
        List<OrderCluster> clusters = new ArrayList<>();

        for (int i = 0; i < sorted.size(); i++) {
            if (assigned[i]) continue;

            // Start new cluster with this seed
            List<Order> members = new ArrayList<>();
            members.add(sorted.get(i));
            assigned[i] = true;

            GeoPoint centroid = sorted.get(i).getPickupPoint();

            // Expand cluster
            for (int j = i + 1; j < sorted.size(); j++) {
                if (assigned[j]) continue;
                double dist = centroid.distanceTo(sorted.get(j).getPickupPoint());
                if (dist <= CLUSTER_RADIUS_METERS) {
                    members.add(sorted.get(j));
                    assigned[j] = true;
                    centroid = recomputeCentroid(members);
                }
            }

            double totalFee = members.stream()
                    .mapToDouble(Order::getQuotedFee).sum();
            double spread = computeSpread(members);

            clusters.add(new OrderCluster(
                    "CL-" + UUID.randomUUID().toString().substring(0, 8),
                    List.copyOf(members),
                    centroid,
                    spread,
                    totalFee
            ));
        }

        return clusters;
    }

    private GeoPoint recomputeCentroid(List<Order> members) {
        double lat = 0, lng = 0;
        for (Order o : members) {
            lat += o.getPickupPoint().lat();
            lng += o.getPickupPoint().lng();
        }
        return new GeoPoint(lat / members.size(), lng / members.size());
    }

    private double computeSpread(List<Order> members) {
        if (members.size() <= 1) return 0;
        double maxDist = 0;
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                double d = members.get(i).getPickupPoint()
                        .distanceTo(members.get(j).getPickupPoint());
                maxDist = Math.max(maxDist, d);
            }
        }
        return maxDist;
    }

    // ── End-zone discovery ──────────────────────────────────────────────

    /**
     * Find top attractive grid cells within MAX_END_ZONE_KM.
     * Scans SpatiotemporalField cells and returns top N by attraction score.
     */
    private List<EndZoneCandidate> findTopEndZones(GeoPoint driverPos) {
        List<EndZoneCandidate> candidates = new ArrayList<>();

        for (int r = 0; r < SpatiotemporalField.ROWS; r++) {
            for (int c = 0; c < SpatiotemporalField.COLS; c++) {
                GeoPoint cellCenter = field.cellCenter(r, c);
                double distKm = driverPos.distanceTo(cellCenter) / 1000.0;
                if (distKm > MAX_END_ZONE_KM || distKm < 0.2) continue;

                double attraction = field.getAttractionAt(cellCenter);
                if (attraction > 0.1) {
                    candidates.add(new EndZoneCandidate(
                            cellCenter, attraction, distKm));
                }
            }
        }

        // Sort by attraction descending, take top N
        candidates.sort((a, b) -> Double.compare(
                b.attractionScore(), a.attractionScore()));
        return candidates.subList(0,
                Math.min(candidates.size(), TOP_END_ZONES));
    }

    // ── Estimation helpers ──────────────────────────────────────────────

    /**
     * Estimate travel speed accounting for traffic and weather.
     * Matches the formula in SimulationEngine.moveDrivers().
     */
    private double estimateSpeed(double trafficIntensity,
                                  WeatherProfile weather) {
        double speedKmh = 30.0 * (1.0 - trafficIntensity * 0.5);
        if (weather == WeatherProfile.HEAVY_RAIN) speedKmh *= 0.7;
        if (weather == WeatherProfile.STORM) speedKmh *= 0.4;
        return Math.max(8.0, speedKmh);
    }

    /**
     * Blend global traffic intensity with local position factors.
     * Drivers in known congested corridors get slightly higher local traffic.
     */
    private double estimateLocalTraffic(GeoPoint pos,
                                         double globalTraffic) {
        // Use driver density as a proxy for local congestion boost
        double densityFactor = Math.min(0.15,
                field.getDriverDensityAt(pos) / 50.0);
        return Math.min(1.0, globalTraffic + densityFactor);
    }

    /**
     * Estimate how long a driver will remain idle if they do not take an order.
     * Based on local demand and shortage pressure.
     *
     * Low demand + low shortage → long idle
     * High demand + high shortage → short idle (orders incoming)
     */
    private double estimateIdleTime(double demand, double shortage) {
        // Inverse relationship: high demand → low idle time
        double normalized = Math.max(0.01, demand * 0.3 + shortage * 0.7);
        return Math.max(0.5, 10.0 / normalized);
    }
}
