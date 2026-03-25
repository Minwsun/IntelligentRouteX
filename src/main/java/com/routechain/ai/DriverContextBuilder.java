package com.routechain.ai;

import com.routechain.ai.DriverDecisionContext.EndZoneCandidate;
import com.routechain.ai.DriverDecisionContext.DropCorridorCandidate;
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
    private static final double BASE_REACHABILITY_HORIZON_MINUTES = 9.0;

    /** Maximum distance (meters) between pickups to be in the same cluster. */
    private static final double CLUSTER_RADIUS_METERS = 1500.0;

    /** Maximum end-zone distance to consider (km). */
    private static final double MAX_END_ZONE_KM = 3.0;

    /** Number of top end-zone candidates to include in context. */
    private static final int TOP_END_ZONES = 3;

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
                                       int simulatedHour,
                                       Instant currentTime) {

        // Cache for synergy computation
        this.currentTrafficIntensity = trafficIntensity;
        this.currentWeather = weather;

        GeoPoint pos = driver.getCurrentLocation();
        double speedKmh = estimateSpeed(trafficIntensity, weather);
        double reachabilityHorizon = computeReachabilityHorizonMinutes(
                trafficIntensity, weather);

        // 1. Find reachable orders
        List<Order> nearby = orderIndex.findReachable(
                pos, speedKmh, reachabilityHorizon + 1.0);
        List<Order> reachable = new ArrayList<>();
        for (Order order : nearby) {
            double travelMinutes = estimateTravelMinutes(pos, order, speedKmh, trafficIntensity);
            double readySlackMinutes = estimateReadySlackMinutes(order, currentTime);
            if (travelMinutes <= reachabilityHorizon
                    || (travelMinutes <= reachabilityHorizon + 0.75
                    && readySlackMinutes <= 1.5)) {
                reachable.add(order);
            }
        }

        // 2. Cluster pickups (DBSCAN-simplified + 8-term synergy)
        List<OrderCluster> clusters = clusterPickups(reachable);

        // 3. Local environment from SpatiotemporalField
        double localDemand = field.getDemandAt(pos);
        double forecast5m = field.getForecastDemandAt(pos, 5);
        double forecast10m = field.getForecastDemandAt(pos, 10);
        double forecast15m = field.getForecastDemandAt(pos, 15);
        double forecast30m = field.getForecastDemandAt(pos, 30);
        double localShortage = field.getShortageAt(pos);
        double localDensity = field.getDriverDensityAt(pos);
        double localSpike = field.getSpikeAt(pos);
        double localWeatherExposure = field.getWeatherExposureAt(pos);
        double localCorridorExposure = field.getCongestionExposureAt(pos);
        double localAttraction = field.getAttractionAt(pos);
        int nearReadyOrders = (int) reachable.stream()
                .filter(o -> estimateReadySlackMinutes(o, currentTime) <= 1.5)
                .count();
        double effectiveSlaSlackMinutes = computeEffectiveSlaSlackMinutes(
                pos, reachable, currentTime, speedKmh, trafficIntensity);
        int nearReadySameMerchantCount = computeNearReadySameMerchantCount(reachable, currentTime);
        int compactClusterCount = (int) clusters.stream()
                .filter(cluster -> cluster.spreadMeters() <= 350.0)
                .count();
        int localReachableBacklog = reachable.size();
        boolean harshWeatherStress = isHarshWeatherStress(weather, localWeatherExposure, localCorridorExposure);
        double thirdOrderFeasibilityScore = computeThirdOrderFeasibilityScore(
                localReachableBacklog,
                compactClusterCount,
                nearReadyOrders,
                nearReadySameMerchantCount,
                effectiveSlaSlackMinutes,
                localWeatherExposure,
                localCorridorExposure,
                localSpike);
        double threeOrderSlackBuffer = computeThreeOrderSlackBuffer(
                effectiveSlaSlackMinutes,
                localWeatherExposure,
                localCorridorExposure,
                currentWeather);
        double waveAssemblyPressure = computeWaveAssemblyPressure(
                localReachableBacklog,
                forecast5m,
                forecast10m,
                localSpike,
                localShortage,
                compactClusterCount,
                nearReadyOrders);

        // 4. Estimate local traffic
        double localTraffic = estimateLocalTraffic(pos, trafficIntensity);

        // 5. End-zone candidates
        List<EndZoneCandidate> endZones = findTopEndZones(pos);
        List<DropCorridorCandidate> dropCorridors = buildDropCorridorCandidates(pos, reachable);
        double deliveryDemandGradient = computeDeliveryDemandGradient(
                localDemand, dropCorridors, endZones);
        double endZoneIdleRisk = computeEndZoneIdleRisk(endZones, localShortage, localDensity);

        // 6. Estimated idle time
        double idleMinutes = estimateIdleTime(localDemand, localShortage);
        StressRegime stressRegime = deriveStressRegime(
                localTraffic, weather, localWeatherExposure,
                localCorridorExposure, localShortage, localReachableBacklog);

        return new DriverDecisionContext(
                driver, reachable, clusters,
                localTraffic, localDemand,
                forecast5m, forecast10m, forecast15m, forecast30m,
                localShortage,
                localDensity, localSpike,
                localWeatherExposure, localCorridorExposure,
                localAttraction, idleMinutes, nearReadyOrders,
                effectiveSlaSlackMinutes, nearReadySameMerchantCount,
                compactClusterCount, localReachableBacklog,
                harshWeatherStress, thirdOrderFeasibilityScore,
                threeOrderSlackBuffer, waveAssemblyPressure,
                deliveryDemandGradient, endZoneIdleRisk, dropCorridors,
                endZones, stressRegime
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
                if (dist <= computeClusterRadiusMeters()) {
                    double synergy = computeSynergy(sorted.get(i), sorted.get(j));
                    if (synergy > computeSynergyThreshold()) {
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

    private double computeEffectiveSlaSlackMinutes(GeoPoint driverPos,
                                                   List<Order> reachable,
                                                   Instant currentTime,
                                                   double speedKmh,
                                                   double trafficIntensity) {
        if (reachable.isEmpty()) {
            return 15.0;
        }

        double minSlack = Double.POSITIVE_INFINITY;
        for (Order order : reachable) {
            double elapsedMinutes = Duration.between(order.getCreatedAt(), currentTime)
                    .toSeconds() / 60.0;
            double travelMinutes = estimateTravelMinutes(driverPos, order, speedKmh, trafficIntensity);
            double readySlackMinutes = Math.max(0.0, estimateReadySlackMinutes(order, currentTime));
            double slack = order.getPromisedEtaMinutes()
                    - elapsedMinutes
                    - travelMinutes
                    - Math.min(4.0, readySlackMinutes);
            minSlack = Math.min(minSlack, slack);
        }
        return minSlack == Double.POSITIVE_INFINITY ? 15.0 : minSlack;
    }

    private int computeNearReadySameMerchantCount(List<Order> reachable, Instant currentTime) {
        java.util.Map<String, Integer> merchantCounts = new java.util.HashMap<>();
        for (Order order : reachable) {
            if (estimateReadySlackMinutes(order, currentTime) > 1.5) {
                continue;
            }
            String merchantId = order.getMerchantId();
            if (merchantId == null || merchantId.isBlank()) {
                continue;
            }
            merchantCounts.merge(merchantId, 1, Integer::sum);
        }

        int total = 0;
        for (Order order : reachable) {
            if (estimateReadySlackMinutes(order, currentTime) > 1.5) {
                continue;
            }
            String merchantId = order.getMerchantId();
            if (merchantId == null || merchantId.isBlank()) {
                continue;
            }
            if (merchantCounts.getOrDefault(merchantId, 0) >= 2) {
                total++;
            }
        }
        return total;
    }

    private StressRegime deriveStressRegime(double localTraffic,
                                            WeatherProfile weather,
                                            double localWeatherExposure,
                                            double localCorridorExposure,
                                            double localShortage,
                                            int localReachableBacklog) {
        double backlogPressure = Math.min(1.0, localReachableBacklog / 8.0);
        double stressScore = localTraffic * 0.28
                + localWeatherExposure * 0.24
                + localCorridorExposure * 0.20
                + Math.min(1.0, localShortage) * 0.18
                + backlogPressure * 0.10;

        if (weather == WeatherProfile.STORM
                || localWeatherExposure >= 0.92
                || localCorridorExposure >= 0.93
                || stressScore >= 0.84) {
            return StressRegime.SEVERE_STRESS;
        }
        if (weather == WeatherProfile.HEAVY_RAIN
                || localTraffic >= 0.58
                || localCorridorExposure >= 0.68
                || stressScore >= 0.60) {
            return StressRegime.STRESS;
        }
        return StressRegime.NORMAL;
    }

    private boolean isHarshWeatherStress(WeatherProfile weather,
                                         double localWeatherExposure,
                                         double localCorridorExposure) {
        return weather == WeatherProfile.HEAVY_RAIN
                || weather == WeatherProfile.STORM
                || localWeatherExposure >= 0.74
                || (localWeatherExposure >= 0.62 && localCorridorExposure >= 0.82);
    }

    private double computeThirdOrderFeasibilityScore(int localReachableBacklog,
                                                     int compactClusterCount,
                                                     int nearReadyOrders,
                                                     int nearReadySameMerchantCount,
                                                     double effectiveSlaSlackMinutes,
                                                     double localWeatherExposure,
                                                     double localCorridorExposure,
                                                     double localSpike) {
        double backlogScore = clamp01(localReachableBacklog / 4.0);
        double clusterScore = clamp01(compactClusterCount / 2.0);
        double readinessScore = clamp01((nearReadyOrders + nearReadySameMerchantCount) / 5.0);
        double slackScore = clamp01(effectiveSlaSlackMinutes / 10.0);
        double spikeScore = clamp01(localSpike * 1.2);
        double exposurePenalty = localWeatherExposure * 0.32 + localCorridorExposure * 0.24;
        return clamp01(
                backlogScore * 0.28
                        + clusterScore * 0.22
                        + readinessScore * 0.20
                        + slackScore * 0.20
                        + spikeScore * 0.10
                        - exposurePenalty);
    }

    private double computeThreeOrderSlackBuffer(double effectiveSlaSlackMinutes,
                                                double localWeatherExposure,
                                                double localCorridorExposure,
                                                WeatherProfile weather) {
        double weatherPenalty = switch (weather) {
            case CLEAR -> 0.0;
            case LIGHT_RAIN -> 0.6;
            case HEAVY_RAIN -> 1.8;
            case STORM -> 2.8;
        };
        return effectiveSlaSlackMinutes
                - localWeatherExposure * 3.2
                - localCorridorExposure * 2.2
                - weatherPenalty;
    }

    private double computeWaveAssemblyPressure(int localReachableBacklog,
                                               double forecast5m,
                                               double forecast10m,
                                               double localSpike,
                                               double localShortage,
                                               int compactClusterCount,
                                               int nearReadyOrders) {
        return clamp01(
                Math.min(1.0, localReachableBacklog / 4.0) * 0.28
                        + Math.min(1.0, forecast5m / 2.0) * 0.20
                        + Math.min(1.0, forecast10m / 2.0) * 0.12
                        + Math.min(1.0, localSpike * 1.2) * 0.14
                        + Math.min(1.0, localShortage) * 0.08
                        + Math.min(1.0, compactClusterCount / 2.0) * 0.10
                        + Math.min(1.0, nearReadyOrders / 4.0) * 0.08);
    }

    // ── End-zone discovery ──────────────────────────────────────────────

    private List<EndZoneCandidate> findTopEndZones(GeoPoint driverPos) {
        List<EndZoneCandidate> candidates = new ArrayList<>();

        for (int r = 0; r < SpatiotemporalField.ROWS; r++) {
            for (int c = 0; c < SpatiotemporalField.COLS; c++) {
                GeoPoint cellCenter = field.cellCenter(r, c);
                double distKm = driverPos.distanceTo(cellCenter) / 1000.0;
                if (distKm > MAX_END_ZONE_KM || distKm < 0.2) continue;

                double weatherExposure = field.getWeatherExposureAt(cellCenter);
                double corridorExposure = field.getCongestionExposureAt(cellCenter);
                double attraction = field.getRiskAdjustedAttractionAt(cellCenter) * 0.45
                        + field.getForecastDemandAt(cellCenter, 10) * 0.30
                        + field.getForecastDemandAt(cellCenter, 15) * 0.15
                        + field.getForecastDemandAt(cellCenter, 30) * 0.10;
                attraction -= weatherExposure * 0.10 + corridorExposure * 0.12;
                if (attraction > 0.1) {
                    candidates.add(new EndZoneCandidate(
                            cellCenter, attraction, distKm,
                            weatherExposure, corridorExposure));
                }
            }
        }

        candidates.sort((a, b) -> Double.compare(
                b.attractionScore(), a.attractionScore()));
        return candidates.subList(0,
                Math.min(candidates.size(), TOP_END_ZONES));
    }

    private List<DropCorridorCandidate> buildDropCorridorCandidates(GeoPoint driverPos,
                                                                    List<Order> reachable) {
        java.util.Map<String, List<Order>> corridorGroups = new java.util.LinkedHashMap<>();
        for (Order order : reachable) {
            String regionId = order.getDropoffRegionId() == null ? "UNK" : order.getDropoffRegionId();
            String directionBucket = dropDirectionBucket(driverPos, order.getDropoffPoint());
            corridorGroups.computeIfAbsent(regionId + ":" + directionBucket, ignored -> new ArrayList<>())
                    .add(order);
        }

        List<DropCorridorCandidate> candidates = new ArrayList<>();
        for (java.util.Map.Entry<String, List<Order>> entry : corridorGroups.entrySet()) {
            List<Order> orders = entry.getValue();
            GeoPoint anchor = computeDropoffCentroid(orders);
            double coherence = computeDropDirectionCoherence(driverPos, orders);
            double demandSignal = field.getForecastDemandAt(anchor, 10) * 0.45
                    + field.getForecastDemandAt(anchor, 15) * 0.25
                    + field.getForecastDemandAt(anchor, 30) * 0.10
                    + field.getShortageAt(anchor) * 0.20;
            double congestionExposure = field.getCongestionExposureAt(anchor);
            double weatherExposure = field.getWeatherExposureAt(anchor);
            double corridorScore = clamp01(
                    coherence * 0.40
                            + Math.min(1.0, orders.size() / 3.0) * 0.20
                            + Math.min(1.0, demandSignal / 2.5) * 0.25
                            - congestionExposure * 0.10
                            - weatherExposure * 0.05);
            candidates.add(new DropCorridorCandidate(
                    entry.getKey(),
                    anchor,
                    corridorScore,
                    demandSignal,
                    congestionExposure,
                    weatherExposure));
        }

        candidates.sort((a, b) -> Double.compare(b.corridorScore(), a.corridorScore()));
        return candidates.subList(0, Math.min(4, candidates.size()));
    }

    private double computeDeliveryDemandGradient(double localDemand,
                                                 List<DropCorridorCandidate> dropCorridors,
                                                 List<EndZoneCandidate> endZones) {
        double bestDropSignal = dropCorridors.stream()
                .mapToDouble(DropCorridorCandidate::demandSignal)
                .max()
                .orElse(0.0);
        double bestEndSignal = endZones.stream()
                .mapToDouble(EndZoneCandidate::attractionScore)
                .max()
                .orElse(0.0);
        return Math.max(0.0, Math.max(bestDropSignal, bestEndSignal) - localDemand);
    }

    private double computeEndZoneIdleRisk(List<EndZoneCandidate> endZones,
                                          double localShortage,
                                          double localDensity) {
        double bestLanding = endZones.stream()
                .mapToDouble(EndZoneCandidate::attractionScore)
                .max()
                .orElse(0.0);
        double densityPenalty = Math.min(0.4, localDensity / 15.0);
        return clamp01(1.0 - Math.min(1.0, bestLanding / 2.0) + densityPenalty - localShortage * 0.15);
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
        double corridorFactor = field.getCongestionExposureAt(pos) * 0.35;
        return Math.min(1.0, globalTraffic + densityFactor + corridorFactor);
    }

    private double estimateIdleTime(double demand, double shortage) {
        double normalized = Math.max(0.01, demand * 0.3 + shortage * 0.7);
        return Math.max(0.5, 10.0 / normalized);
    }

    private double estimateTravelMinutes(GeoPoint driverPos, Order order,
                                         double speedKmh, double trafficIntensity) {
        double distanceKm = driverPos.distanceTo(order.getPickupPoint()) / 1000.0;
        double startExposure = field.getCongestionExposureAt(driverPos);
        double pickupExposure = field.getCongestionExposureAt(order.getPickupPoint());
        double weatherExposure = field.getWeatherExposureAt(order.getPickupPoint());
        double congestionFactor = 1.0
                + trafficIntensity * 0.25
                + ((startExposure + pickupExposure) * 0.5) * 0.20
                + weatherExposure * 0.10;
        return (distanceKm / Math.max(8.0, speedKmh)) * 60.0 * congestionFactor;
    }

    private double computeReachabilityHorizonMinutes(double trafficIntensity,
                                                     WeatherProfile weather) {
        double horizon = BASE_REACHABILITY_HORIZON_MINUTES;
        horizon += switch (weather) {
            case CLEAR -> 0.8;
            case LIGHT_RAIN -> 0.2;
            case HEAVY_RAIN -> -1.6;
            case STORM -> -2.4;
        };
        return Math.max(5.0, horizon);
    }

    private double computeClusterRadiusMeters() {
        double radius = CLUSTER_RADIUS_METERS;
        if (currentWeather == WeatherProfile.CLEAR) {
            radius += 180.0;
        } else if (currentWeather == WeatherProfile.LIGHT_RAIN) {
            radius += 80.0;
        }
        if (currentTrafficIntensity > 0.75) {
            radius -= 200.0;
        }
        radius -= switch (currentWeather) {
            case CLEAR -> 0.0;
            case LIGHT_RAIN -> 80.0;
            case HEAVY_RAIN -> 300.0;
            case STORM -> 500.0;
        };
        return Math.max(650.0, radius);
    }

    private String dropDirectionBucket(GeoPoint origin, GeoPoint dropoff) {
        double angle = Math.atan2(dropoff.lat() - origin.lat(), dropoff.lng() - origin.lng());
        double normalized = angle < 0 ? angle + Math.PI * 2 : angle;
        int bucket = (int) Math.floor(normalized / (Math.PI / 4.0));
        return "DIR-" + bucket;
    }

    private GeoPoint computeDropoffCentroid(List<Order> orders) {
        double lat = 0.0;
        double lng = 0.0;
        for (Order order : orders) {
            lat += order.getDropoffPoint().lat();
            lng += order.getDropoffPoint().lng();
        }
        return new GeoPoint(lat / Math.max(1, orders.size()), lng / Math.max(1, orders.size()));
    }

    private double computeDropDirectionCoherence(GeoPoint origin, List<Order> orders) {
        if (orders.size() <= 1) {
            return 1.0;
        }

        double total = 0.0;
        int pairs = 0;
        for (int i = 0; i < orders.size(); i++) {
            double dx1 = orders.get(i).getDropoffPoint().lng() - origin.lng();
            double dy1 = orders.get(i).getDropoffPoint().lat() - origin.lat();
            for (int j = i + 1; j < orders.size(); j++) {
                double dx2 = orders.get(j).getDropoffPoint().lng() - origin.lng();
                double dy2 = orders.get(j).getDropoffPoint().lat() - origin.lat();
                double dot = dx1 * dx2 + dy1 * dy2;
                double mag = Math.sqrt(dx1 * dx1 + dy1 * dy1) * Math.sqrt(dx2 * dx2 + dy2 * dy2);
                total += mag > 1e-9 ? (dot / mag + 1.0) / 2.0 : 0.5;
                pairs++;
            }
        }
        return pairs > 0 ? total / pairs : 0.5;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double computeSynergyThreshold() {
        double threshold = switch (currentWeather) {
            case CLEAR -> 0.26;
            case LIGHT_RAIN -> 0.28;
            case HEAVY_RAIN -> 0.35;
            case STORM -> 0.40;
        };
        if (currentTrafficIntensity > 0.65) {
            threshold += 0.02;
        }
        return Math.min(0.42, threshold);
    }

    private double estimateReadySlackMinutes(Order order, Instant currentTime) {
        if (order.getPredictedReadyAt() == null) return 0.0;
        long seconds = Duration.between(currentTime, order.getPredictedReadyAt()).getSeconds();
        return Math.max(0.0, seconds / 60.0);
    }
}
