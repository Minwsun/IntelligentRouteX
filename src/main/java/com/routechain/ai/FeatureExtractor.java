package com.routechain.ai;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;
import com.routechain.graph.FutureCellValue;
import com.routechain.graph.GraphFeatureNamespaces;
import com.routechain.graph.GraphShadowSnapshot;
import com.routechain.infra.FeatureStore;
import com.routechain.simulation.DispatchPlan;

import java.util.List;
import java.util.Map;

/**
 * Standardized feature engineering for all AI models.
 * Single source of truth for feature vector construction.
 *
 * Produces:
 * - Plan features (15D) — scoring, ranking, horizon evaluation
 * - End-state features (10D) — continuation value prediction
 * - Context features (8D) — policy selection
 * - ETA features (6D) — ETA prediction
 * - Risk features (7D) — late risk prediction
 * - Cancel features (5D) — cancellation risk prediction
 */
public class FeatureExtractor {
    private final FeatureStore featureStore;

    public FeatureExtractor() {
        this(null);
    }

    public FeatureExtractor(FeatureStore featureStore) {
        this.featureStore = featureStore;
    }

    // ── Plan features (15D) ─────────────────────────────────────────────

    /**
     * Extract 15-dimensional feature vector for a dispatch plan.
     */
    public double[] planFeatures(DispatchPlan plan, SpatiotemporalField field,
                                  double trafficIntensity, WeatherProfile weather) {
        double[] f = new double[15];
        List<Order> orders = plan.getOrders();
        Driver driver = plan.getDriver();
        Map<String, Object> traceTraffic = traceTrafficFeatures(plan);
        GeoPoint endPt = plan.getEndZonePoint();
        Map<String, Object> endZoneTraffic = zoneTrafficFeatures(field, endPt);
        double pickupFriction = numericFeature(traceTraffic, "pickupFrictionScore", plan.getPickupFrictionScore());
        double dropReachability = numericFeature(traceTraffic, "dropReachabilityScore", plan.getDropReachabilityScore());
        double corridorCongestion = numericFeature(traceTraffic, "corridorCongestionScore", plan.getCorridorCongestionScore());
        double zoneSlowdown = Math.max(
                numericFeature(traceTraffic, "zoneSlowdownIndex", plan.getZoneSlowdownIndex()),
                numericFeature(endZoneTraffic, "slowdownIndex", 0.0));
        double travelTimeDrift = numericFeature(traceTraffic, "travelTimeDriftScore", plan.getTravelTimeDriftScore());

        // 0: bundleSize
        f[0] = orders.size() / 5.0;

        // 1: pickupSpreadKm — max pairwise distance between pickups
        f[1] = computePickupSpread(orders) / 5.0; // normalize to ~0-1

        // 2: dropoffDirectionCoherence
        f[2] = computeDropoffCoherence(orders);

        // 3: predictedETAMinutes (normalized)
        f[3] = plan.getPredictedTotalMinutes() / 60.0;

        // 4: lateRisk
        f[4] = plan.getLateRisk();

        // 5: cancelRisk
        f[5] = plan.getCancellationRisk();

        // 6: driverProfitNorm
        f[6] = Math.max(-1, Math.min(1, plan.getDriverProfit() / 50000.0));

        // 7: customerFeeNorm
        f[7] = plan.getCustomerFee() / 35000.0;

        // 8: deadheadKm
        f[8] = clamp01(
                Math.min(1.0, plan.getPredictedDeadheadKm() / 6.0)
                        * (1.0 + pickupFriction * 0.18 + travelTimeDrift * 0.10));

        // 9: continuationValue
        f[9] = plan.getEndZoneOpportunity();

        // 10: post-drop opportunity
        double basePostDrop = field != null ? field.getPostDropOpportunityAt(endPt, 10) : 0.3;
        double zoneReachability = numericFeature(endZoneTraffic, "dropReachabilityScore", dropReachability);
        f[10] = clamp01(basePostDrop * 0.70 + Math.max(dropReachability, zoneReachability) * 0.30);

        // 11: endZoneDriverDensity
        f[11] = field != null ? Math.min(1.0, field.getDriverDensityAt(endPt) / 5.0) : 0.3;

        // 12: congestionExposure
        double baseCongestion = field != null
                ? Math.min(1.0, Math.max(trafficIntensity, field.getTrafficForecastAt(endPt, 10)))
                : Math.min(1.0, trafficIntensity);
        f[12] = clamp01(Math.max(baseCongestion, Math.max(corridorCongestion, zoneSlowdown)));

        // 13: detourRatio
        double standaloneDist = computeStandaloneDistance(orders);
        double bundleDist = computeBundleRouteDistance(plan);
        f[13] = standaloneDist > 0
                ? Math.min(2.0, bundleDist / standaloneDist) / 2.0 : 0.5;

        // 14: acceptanceProbability
        f[14] = driver.getPredictedAcceptanceProb();

        return f;
    }

    // ── End-state features (10D) ────────────────────────────────────────

    /**
     * Extract 10-dimensional feature vector for end-state evaluation.
     */
    public double[] endStateFeatures(GeoPoint endPos, int endHour,
                                      SpatiotemporalField field,
                                      WeatherProfile weather, Driver driver) {
        double[] f = new double[10];
        Map<String, Object> zoneTraffic = zoneTrafficFeatures(field, endPos);

        f[0] = field != null ? Math.min(1.0, field.getDemandAt(endPos) / 3.0) : 0.3;
        double basePostDrop = field != null ? field.getPostDropOpportunityAt(endPos, 10) : 0.3;
        double dropReachability = numericFeature(zoneTraffic, "dropReachabilityScore", 0.0);
        f[1] = clamp01(basePostDrop * 0.72 + dropReachability * 0.28);
        f[2] = field != null ? field.getSpikeAt(endPos) : 0.1;
        f[3] = field != null ? Math.min(1.0, field.getDriverDensityAt(endPos) / 5.0) : 0.3;
        f[4] = field != null ? field.getShortageForecastAt(endPos, 10) : 0.3;
        double baseTraffic = field != null ? field.getTrafficForecastAt(endPos, 10) : 0.3;
        f[5] = clamp01(Math.max(baseTraffic, numericFeature(zoneTraffic, "slowdownIndex", 0.0)));
        f[6] = endHour / 24.0;
        f[7] = field != null
                ? Math.max(
                Math.max(weather.ordinal() / 3.0, field.getWeatherForecastAt(endPos, 10)),
                numericFeature(zoneTraffic, "weatherSeverity", 0.0))
                : weather.ordinal() / 3.0;
        f[8] = driver.getComputedUtilization();
        f[9] = Math.min(1.0, driver.getCompletedOrders() / 20.0);

        return f;
    }

    // ── Context features (8D) ───────────────────────────────────────────

    /**
     * Extract 8-dimensional context vector for policy selection.
     */
    public double[] contextFeatures(double traffic, WeatherProfile weather,
                                     int hour, double shortage, double avgWait,
                                     int pendingOrders, int availableDrivers,
                                     double surgeLevel) {
        double[] f = new double[8];

        f[0] = traffic;
        f[1] = weather.ordinal() / 3.0;
        f[2] = hour / 24.0;
        f[3] = Math.min(1.0, shortage);
        f[4] = Math.min(1.0, avgWait / 15.0);
        f[5] = Math.min(1.0, pendingOrders / 30.0);
        f[6] = Math.min(1.0, availableDrivers / 20.0);
        f[7] = Math.min(1.0, surgeLevel);

        return f;
    }

    // ── ETA features (6D) ───────────────────────────────────────────────

    /**
     * Extract features for ETA prediction model.
     */
    public double[] etaFeatures(double distanceKm, double traffic,
                                 WeatherProfile weather, int hour,
                                 int bundleSize, double congestion) {
        return new double[] {
            Math.min(1.0, distanceKm / 15.0),
            traffic,
            weather.ordinal() / 3.0,
            hour / 24.0,
            bundleSize / 5.0,
            Math.min(1.0, congestion)
        };
    }

    // ── Late risk features (7D) ─────────────────────────────────────────

    /**
     * Extract features for late risk prediction model.
     */
    public double[] lateRiskFeatures(double distanceKm, double traffic,
                                      WeatherProfile weather, int hour,
                                      int bundleSize, double slaSlackMinutes,
                                      double pickupDelay) {
        return new double[] {
            Math.min(1.0, distanceKm / 15.0),
            traffic,
            weather.ordinal() / 3.0,
            hour / 24.0,
            bundleSize / 5.0,
            Math.max(-1, Math.min(1, slaSlackMinutes / 30.0)),
            Math.min(1.0, pickupDelay / 10.0)
        };
    }

    // ── Cancel risk features (5D) ───────────────────────────────────────

    /**
     * Extract features for cancellation risk prediction model.
     */
    public double[] cancelFeatures(double waitTimeMinutes, double lateRisk,
                                    double fee, WeatherProfile weather,
                                    int bundleSize) {
        return new double[] {
            Math.min(1.0, waitTimeMinutes / 15.0),
            lateRisk,
            Math.min(1.0, fee / 50000.0),
            weather.ordinal() / 3.0,
            bundleSize / 5.0
        };
    }

    // ── Pickup delay features (4D) ──────────────────────────────────────

    /**
     * Extract features for pickup delay prediction.
     */
    public double[] pickupDelayFeatures(double traffic, WeatherProfile weather,
                                         double distanceKm, int hour) {
        return new double[] {
            traffic,
            weather.ordinal() / 3.0,
            Math.min(1.0, distanceKm / 10.0),
            hour / 24.0
        };
    }

    /**
     * Extract features for learned batch admission.
     */
    public double[] batchValueFeatures(DispatchPlan plan) {
        double bundleSize = Math.max(0.0, Math.min(1.0, (plan.getBundleSize() - 1) / 4.0));
        double pickupCompactness = clamp01(1.0 - plan.getPickupSpreadKm() / 2.5);
        double dropCoherence = clamp01(1.0 - plan.getDeliveryZigZagPenalty());
        double bundleEfficiency = clamp01(plan.getBundleEfficiency());
        double onTime = clamp01(plan.getOnTimeProbability());
        double lateRisk = clamp01(plan.getLateRisk());
        double deadhead = clamp01(plan.getPredictedDeadheadKm() / 4.0);
        double postDrop = clamp01(plan.getPostDropDemandProbability());
        double landing = clamp01(plan.getLastDropLandingScore());
        double emptyRisk = clamp01(plan.getEmptyRiskAfter());
        double borrowedDependency = clamp01(plan.getBorrowedDependencyScore());
        double zigZag = clamp01(plan.getDeliveryZigZagPenalty());
        return new double[] {
                bundleSize,
                pickupCompactness,
                dropCoherence,
                bundleEfficiency,
                onTime,
                lateRisk,
                deadhead,
                postDrop,
                landing,
                emptyRisk,
                borrowedDependency,
                zigZag
        };
    }

    /**
     * Extract features for fallback rescue gating under stress.
     */
    public double[] stressRescueFeatures(DispatchPlan plan,
                                         DriverDecisionContext ctx,
                                         SpatiotemporalField field,
                                         double traffic,
                                         WeatherProfile weather) {
        GeoPoint pickupPoint = plan.getSequence().isEmpty()
                ? plan.getDriver().getCurrentLocation()
                : plan.getSequence().get(0).location();
        double stressIntensity = clamp01(Math.max(
                weather.ordinal() / 3.0,
                Math.max(traffic, plan.getTrafficExposureScore())));
        double onTime = clamp01(plan.getOnTimeProbability());
        double deadhead = clamp01(plan.getPredictedDeadheadKm() / 4.0);
        double pickupReady = clamp01(1.0 - Math.min(1.0, plan.getMerchantPrepRiskScore()));
        double sameZone = plan.getDriver().getRegionId().equals(
                plan.getOrders().isEmpty()
                        ? plan.getDriver().getRegionId()
                        : plan.getOrders().get(0).getPickupRegionId()) ? 1.0 : 0.0;
        double localBacklogTight = ctx == null
                ? 0.5
                : clamp01(1.0 - Math.min(1.0, ctx.localReachableBacklog() / 4.0));
        double borrowRisk = clamp01(plan.getBorrowedDependencyScore());
        double merchantPrepRisk = clamp01(plan.getMerchantPrepRiskScore());
        double weatherExposure = field == null ? weather.ordinal() / 3.0 : field.getWeatherExposureAt(pickupPoint);
        double trafficExposure = field == null ? traffic : field.getCongestionExposureAt(pickupPoint);
        return new double[] {
                stressIntensity,
                onTime,
                deadhead,
                pickupReady,
                sameZone,
                localBacklogTight,
                borrowRisk,
                merchantPrepRisk,
                clamp01(weatherExposure),
                clamp01(Math.max(traffic, trafficExposure))
        };
    }

    /**
     * Extract features for end-zone and reposition positioning value.
     */
    public double[] positioningFeatures(GeoPoint currentPos,
                                        GeoPoint targetPos,
                                        SpatiotemporalField field,
                                        GraphShadowSnapshot snapshot,
                                        double traffic,
                                        WeatherProfile weather) {
        Map<String, Object> zoneTraffic = zoneTrafficFeatures(field, targetPos);
        double dropReachability = numericFeature(zoneTraffic, "dropReachabilityScore", 0.0);
        double demand5 = field == null ? 0.0 : clamp01(field.getForecastDemandAt(targetPos, 5) / 3.0);
        double demand10 = field == null ? 0.0 : clamp01(field.getForecastDemandAt(targetPos, 10) / 3.0);
        double demand15 = field == null ? 0.0 : clamp01(field.getForecastDemandAt(targetPos, 15) / 3.0);
        demand10 = clamp01(demand10 * 0.85 + dropReachability * 0.15);
        demand15 = clamp01(demand15 * 0.90 + dropReachability * 0.10);
        double postDrop = field == null ? 0.0 : clamp01(field.getPostDropOpportunityAt(targetPos, 10));
        double emptyRisk = field == null ? 0.5 : clamp01(field.getEmptyZoneRiskAt(targetPos, 10));
        double graphCentrality = resolveFutureCellValue(targetPos, field, snapshot).graphCentralityScore();
        double shortage = field == null ? 0.0 : clamp01(field.getShortageForecastAt(targetPos, 10));
        double congestion = field == null ? traffic : clamp01(Math.max(traffic, field.getTrafficForecastAt(targetPos, 10)));
        congestion = clamp01(Math.max(congestion, numericFeature(zoneTraffic, "slowdownIndex", 0.0)));
        double weatherExposure = field == null
                ? weather.ordinal() / 3.0
                : clamp01(Math.max(weather.ordinal() / 3.0, field.getWeatherForecastAt(targetPos, 10)));
        double distancePenalty = currentPos == null || targetPos == null
                ? 0.0
                : clamp01((currentPos.distanceTo(targetPos) / 1000.0) / 2.0);
        double attraction = field == null ? 0.0 : clamp01(field.getRiskAdjustedAttractionAt(targetPos));
        attraction = clamp01(Math.max(attraction, dropReachability * 0.82));
        return new double[] {
                demand5,
                demand10,
                demand15,
                postDrop,
                emptyRisk,
                clamp01(graphCentrality),
                shortage,
                congestion,
                weatherExposure,
                distancePenalty,
                attraction
        };
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private double computePickupSpread(List<Order> orders) {
        if (orders.size() <= 1) return 0;
        double maxDist = 0;
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                double d = orders.get(i).getPickupPoint()
                        .distanceTo(orders.get(j).getPickupPoint()) / 1000.0;
                maxDist = Math.max(maxDist, d);
            }
        }
        return maxDist;
    }

    private double computeDropoffCoherence(List<Order> orders) {
        if (orders.size() <= 1) return 1.0;

        // Average cosine similarity of dropoff direction vectors from pickup centroid
        double cLat = 0, cLng = 0;
        for (Order o : orders) {
            cLat += o.getPickupPoint().lat();
            cLng += o.getPickupPoint().lng();
        }
        cLat /= orders.size();
        cLng /= orders.size();

        double totalCos = 0;
        int pairs = 0;
        for (int i = 0; i < orders.size(); i++) {
            double dx1 = orders.get(i).getDropoffPoint().lng() - cLng;
            double dy1 = orders.get(i).getDropoffPoint().lat() - cLat;
            for (int j = i + 1; j < orders.size(); j++) {
                double dx2 = orders.get(j).getDropoffPoint().lng() - cLng;
                double dy2 = orders.get(j).getDropoffPoint().lat() - cLat;
                double dot = dx1 * dx2 + dy1 * dy2;
                double mag = Math.sqrt(dx1 * dx1 + dy1 * dy1) * Math.sqrt(dx2 * dx2 + dy2 * dy2);
                totalCos += mag > 1e-9 ? (dot / mag + 1.0) / 2.0 : 0.5;
                pairs++;
            }
        }
        return pairs > 0 ? totalCos / pairs : 0.5;
    }

    private double computeStandaloneDistance(List<Order> orders) {
        double total = 0;
        for (Order o : orders) {
            total += o.getPickupPoint().distanceTo(o.getDropoffPoint()) / 1000.0;
        }
        return total;
    }

    private double computeBundleRouteDistance(DispatchPlan plan) {
        double dist = 0;
        GeoPoint prev = plan.getDriver().getCurrentLocation();
        for (DispatchPlan.Stop stop : plan.getSequence()) {
            dist += prev.distanceTo(stop.location()) / 1000.0;
            prev = stop.location();
        }
        return dist;
    }

    private FutureCellValue resolveFutureCellValue(GeoPoint targetPos,
                                                   SpatiotemporalField field,
                                                   GraphShadowSnapshot snapshot) {
        if (targetPos == null || field == null || snapshot == null) {
            return new FutureCellValue("cell-unknown", "instant", 10, 0.0, 0.0, 1.0, 0.0, 0.0, "missing");
        }
        String cellId = field.cellKeyOf(targetPos);
        return snapshot.futureCellValues().stream()
                .filter(value -> value.cellId().equals(cellId))
                .findFirst()
                .orElse(new FutureCellValue(
                        cellId,
                        "instant",
                        10,
                        field.getForecastDemandAt(targetPos, 10),
                        field.getPostDropOpportunityAt(targetPos, 10),
                        field.getEmptyZoneRiskAt(targetPos, 10),
                        0.0,
                        field.getRiskAdjustedAttractionAt(targetPos),
                        "fallback"));
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private Map<String, Object> traceTrafficFeatures(DispatchPlan plan) {
        if (featureStore == null || plan == null || plan.getTraceId() == null || plan.getTraceId().isBlank()) {
            return Map.of();
        }
        return featureStore.get(GraphFeatureNamespaces.TRAFFIC_FEATURES, "latest:trace:" + plan.getTraceId())
                .orElse(Map.of());
    }

    private Map<String, Object> zoneTrafficFeatures(SpatiotemporalField field, GeoPoint point) {
        if (featureStore == null || field == null || point == null) {
            return Map.of();
        }
        String cellId = field.cellKeyOf(point);
        if (cellId == null || cellId.isBlank()) {
            return Map.of();
        }
        return featureStore.get(GraphFeatureNamespaces.TRAFFIC_FEATURES, "latest:zone:" + cellId)
                .orElse(Map.of());
    }

    private double numericFeature(Map<String, Object> features, String key, double fallback) {
        if (features == null || features.isEmpty()) {
            return fallback;
        }
        Object value = features.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }
}
