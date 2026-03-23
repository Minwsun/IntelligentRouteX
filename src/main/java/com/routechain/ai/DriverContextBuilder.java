package com.routechain.ai;

import com.routechain.ai.DriverDecisionContext.EndZoneCandidate;
import com.routechain.ai.DriverDecisionContext.OrderCluster;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;

import java.time.Duration;
import java.time.Instant;
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
 * 8-term synergy scoring for cluster quality evaluation.
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

    /** Minimum synergy threshold for cluster inclusion. */
    private static final double SYNERGY_THRESHOLD = 0.35;

    private final NearbyOrderIndexer orderIndex;
    private final SpatiotemporalField field;

    // Cached per-tick context for synergy penalty terms
    private double currentTrafficIntensity = 0;
    private WeatherProfile currentWeather = WeatherProfile.CLEAR;

    public DriverContextBuilder(NearbyOrderIndexer orderIndex,
                                 SpatiotemporalField field) {
        this.orderIndex = orderIndex;
        this.field = field;
    }

    // ── Public API ──────────────────────────────────────────────────────

    public void rebuildIndex(List<Order> pendingOrders) {
        orderIndex.rebuild(pendingOrders);
    }

    /**
     * Build a complete local-world snapshot for a single driver.
     */
    public DriverDecisionContext build(Driver driver,
                                        double trafficIntensity,
                                        WeatherProfile weather,
                                        int simulatedHour) {

        // Cache for synergy computation
        this.currentTrafficIntensity = trafficIntensity;
        this.currentWeather = weather;

        GeoPoint pos = driver.getCurrentLocation();
        double speedKmh = estimateSpeed(trafficIntensity, weather);

        // 1. Find reachable orders
        List<Order> reachable = orderIndex.findReachable(
                pos, speedKmh, REACHABILITY_HORIZON_MINUTES);

        // 2. Cluster pickups (DBSCAN-simplified + 8-term synergy)
        List<OrderCluster> clusters = clusterPickups(reachable);

        // 3. Local environment from SpatiotemporalField
        double localDemand = field.getDemandAt(pos);
        double localShortage = field.getShortageAt(pos);
        double localDensity = field.getDriverDensityAt(pos);
        double localSpike = field.getSpikeAt(pos);
        double localAttraction = field.getAttractionAt(pos);

        // 4. Estimate local traffic
        double localTraffic = estimateLocalTraffic(pos, trafficIntensity);

        // 5. End-zone candidates
        List<EndZoneCandidate> endZones = findTopEndZones(pos);

        // 6. Estimated idle time
        double idleMinutes = estimateIdleTime(localDemand, localShortage);

        return new DriverDecisionContext(
                driver, reachable, clusters,
                localTraffic, localDemand, localShortage,
                localDensity, localSpike,
                localAttraction, idleMinutes, endZones
        );
    }

    // ── Clustering with 8-term synergy ──────────────────────────────────

    /**
     * Simplified DBSCAN — single-pass greedy clustering.
     * Orders are only added to a cluster if their synergy with the seed
     * exceeds the threshold (0.35).
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

            List<Order> members = new ArrayList<>();
            members.add(sorted.get(i));
            assigned[i] = true;

            GeoPoint centroid = sorted.get(i).getPickupPoint();

            for (int j = i + 1; j < sorted.size(); j++) {
                if (assigned[j]) continue;
                double dist = centroid.distanceTo(sorted.get(j).getPickupPoint());
                if (dist <= CLUSTER_RADIUS_METERS) {
                    double synergy = computeSynergy(sorted.get(i), sorted.get(j));
                    if (synergy > SYNERGY_THRESHOLD) {
                        members.add(sorted.get(j));
                        assigned[j] = true;
                        centroid = recomputeCentroid(members);
                    }
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

    /**
     * 8-term synergy score between seed order and candidate order.
     *
     * Positive terms (attraction):
     *   +0.25 pickupCloseness
     *   +0.20 sameMerchantOrCluster
     *   +0.15 directionSimilarity
     *   +0.10 etaSlackCompatibility
     *   +0.10 merchantReadinessCompatibility
     *   +0.10 districtTransitionGain
     *
     * Negative terms (penalty):
     *   -0.10 pickupConflictPenalty
     *   -0.10 weatherTrafficRisk
     *
     * @return synergy score in [0..0.90] range (theoretical max)
     */
    private double computeSynergy(Order seed, Order candidate) {
        double synergy = 0;

        // ── Term 1: Pickup Closeness (0 to 0.25) ────────────────────────
        double dist = seed.getPickupPoint().distanceTo(candidate.getPickupPoint());
        double closeness = Math.max(0, 1.0 - (dist / CLUSTER_RADIUS_METERS));
        synergy += 0.25 * closeness;

        // ── Term 2: Same Merchant or Cluster (0 to 0.20) ────────────────
        boolean sameMerchant = false;
        String seedMerchant = seed.getMerchantId();
        String candMerchant = candidate.getMerchantId();
        if (seedMerchant != null && !seedMerchant.isEmpty()
                && seedMerchant.equals(candMerchant)) {
            sameMerchant = true;
        }
        synergy += 0.20 * (sameMerchant ? 1.0 : 0.0);

        // ── Term 3: Direction Similarity (0 to 0.15) ────────────────────
        double dx1 = seed.getDropoffPoint().lng() - seed.getPickupPoint().lng();
        double dy1 = seed.getDropoffPoint().lat() - seed.getPickupPoint().lat();
        double dx2 = candidate.getDropoffPoint().lng() - candidate.getPickupPoint().lng();
        double dy2 = candidate.getDropoffPoint().lat() - candidate.getPickupPoint().lat();
        double dotProduct = dx1 * dx2 + dy1 * dy2;
        double mag1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
        double mag2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);
        double directionSim = (mag1 > 0 && mag2 > 0)
                ? Math.max(0, dotProduct / (mag1 * mag2)) : 0;
        synergy += 0.15 * directionSim;

        // ── Term 4: ETA Slack Compatibility (0 to 0.10) ─────────────────
        Instant seedCreated = seed.getCreatedAt();
        Instant candCreated = candidate.getCreatedAt();
        if (seedCreated != null && candCreated != null) {
            long diffMs = Math.abs(Duration.between(seedCreated, candCreated).toMillis());
            double slackCompat = Math.max(0, 1.0 - (diffMs / (10.0 * 60 * 1000)));
            synergy += 0.10 * slackCompat;
        }

        // ── Term 5: Merchant Readiness Compatibility (0 to 0.10) ────────
        // Both orders should have similar readiness — if one is much later, penalty
        Instant seedReady = seed.getPredictedReadyAt();
        Instant candReady = candidate.getPredictedReadyAt();
        if (seedReady != null && candReady != null) {
            long readyDiffMs = Math.abs(Duration.between(seedReady, candReady).toMillis());
            double readyCompat = Math.max(0, 1.0 - (readyDiffMs / (5.0 * 60 * 1000)));
            synergy += 0.10 * readyCompat;
        } else {
            // If readiness data missing, give partial credit
            synergy += 0.05;
        }

        // ── Term 6: District Transition Gain (0 to 0.10) ────────────────
        boolean sameDropRegion = seed.getDropoffRegionId()
                .equals(candidate.getDropoffRegionId());
        synergy += 0.10 * (sameDropRegion ? 1.0 : 0.0);

        // ── Term 7: Pickup Conflict Penalty (0 to -0.10) ────────────────
        // If both orders have high delay hazard, combining them risks compounding waits
        double seedHazard = seed.getPickupDelayHazard();
        double candHazard = candidate.getPickupDelayHazard();
        double conflictPenalty = (seedHazard + candHazard) / 2.0; // 0..1 range
        synergy -= 0.10 * conflictPenalty;

        // ── Term 8: Weather/Traffic Risk Penalty (0 to -0.10) ───────────
        // High traffic + bad weather makes multi-pickup riskier
        double weatherRisk = 0;
        if (currentWeather == WeatherProfile.HEAVY_RAIN) weatherRisk = 0.5;
        if (currentWeather == WeatherProfile.STORM) weatherRisk = 1.0;
        double envRisk = (currentTrafficIntensity * 0.6 + weatherRisk * 0.4);
        synergy -= 0.10 * envRisk;

        return Math.max(0, synergy);
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

        candidates.sort((a, b) -> Double.compare(
                b.attractionScore(), a.attractionScore()));
        return candidates.subList(0,
                Math.min(candidates.size(), TOP_END_ZONES));
    }

    // ── Estimation helpers ──────────────────────────────────────────────

    private double estimateSpeed(double trafficIntensity,
                                  WeatherProfile weather) {
        double speedKmh = 30.0 * (1.0 - trafficIntensity * 0.5);
        if (weather == WeatherProfile.HEAVY_RAIN) speedKmh *= 0.7;
        if (weather == WeatherProfile.STORM) speedKmh *= 0.4;
        return Math.max(8.0, speedKmh);
    }

    private double estimateLocalTraffic(GeoPoint pos,
                                         double globalTraffic) {
        double densityFactor = Math.min(0.15,
                field.getDriverDensityAt(pos) / 50.0);
        return Math.min(1.0, globalTraffic + densityFactor);
    }

    private double estimateIdleTime(double demand, double shortage) {
        double normalized = Math.max(0.01, demand * 0.3 + shortage * 0.7);
        return Math.max(0.5, 10.0 / normalized);
    }
}
